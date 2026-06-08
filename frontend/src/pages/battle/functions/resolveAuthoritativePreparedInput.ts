import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleVector2 as Vec2 } from "../../../objects/battle/objects/core/BattleCoreScalars";
import type { AuthoritativeBattleInputSnapshot } from "../input/authoritativeBattleInput";

const RUNTIME_TOGGLE_DEDUP_WINDOW_MS = 160;

let lastFallbackBlinkToggleAtMs = Number.NEGATIVE_INFINITY;
let lastFallbackFreezeToggleAtMs = Number.NEGATIVE_INFINITY;

export interface AuthoritativePreparedInputResolution {
  input: AuthoritativeBattleInputSnapshot;
  preparedSkill: PreparedSkill;
  confirmedTarget: Vec2 | null;
}

type TargetedPreparedSkill = Exclude<PreparedSkill, null>;

interface PreparedSkillTransitionInput {
  readonly primaryJustPressed: boolean;
  readonly toggleBlink: boolean;
  readonly toggleFreeze: boolean;
}

interface PreparedSkillTransition {
  preparedSkill: PreparedSkill;
  castSkill: TargetedPreparedSkill | null;
  toggledPreparedSkill: boolean;
}

export function resolveAuthoritativePreparedInput(
  runtimeCommand: PlayerCommand | null,
  fallback: AuthoritativeBattleInputSnapshot,
  preparedSkill: PreparedSkill
): AuthoritativePreparedInputResolution {
  const nowMs = readNowMs();
  if (fallback.castBlink) {
    lastFallbackBlinkToggleAtMs = nowMs;
  }
  if (fallback.castFreeze) {
    lastFallbackFreezeToggleAtMs = nowMs;
  }
  const rawRuntimeToggleBlink = runtimeCommand?.toggleBlink ?? false;
  const rawRuntimeToggleFreeze = runtimeCommand?.toggleFreeze ?? false;
  const runtimeToggleBlinkSuppressed =
    rawRuntimeToggleBlink && nowMs - lastFallbackBlinkToggleAtMs <= RUNTIME_TOGGLE_DEDUP_WINDOW_MS;
  const runtimeToggleFreezeSuppressed =
    rawRuntimeToggleFreeze && nowMs - lastFallbackFreezeToggleAtMs <= RUNTIME_TOGGLE_DEDUP_WINDOW_MS;
  const runtimeToggleBlink = rawRuntimeToggleBlink && !runtimeToggleBlinkSuppressed;
  const runtimeToggleFreeze = rawRuntimeToggleFreeze && !runtimeToggleFreezeSuppressed;
  const input = runtimeCommand
    ? toAuthoritativeInputSnapshot(runtimeCommand, fallback)
    : toFallbackAuthoritativeInputSnapshot(fallback);
  const transition = resolvePreparedSkillTransition(preparedSkill, {
    primaryJustPressed: input.primaryJustPressed,
    toggleBlink: runtimeToggleBlink || fallback.castBlink,
    toggleFreeze: runtimeToggleFreeze || fallback.castFreeze
  });
  recordAuthoritativePreparedInputDiagnostics({
    preparedSkillBefore: preparedSkill,
    primaryJustPressed: input.primaryJustPressed,
    fallbackCastBlink: fallback.castBlink,
    fallbackCastFreeze: fallback.castFreeze,
    runtimeToggleBlink,
    runtimeToggleFreeze,
    rawRuntimeToggleBlink,
    rawRuntimeToggleFreeze,
    runtimeToggleBlinkSuppressed,
    runtimeToggleFreezeSuppressed,
    preparedSkillAfter: transition.preparedSkill,
    castSkill: transition.castSkill,
    inputCastDash: input.castDash,
    inputCastCritical: input.castCritical,
    outputCastBlink: transition.castSkill === "Blink",
    outputCastFreeze: transition.castSkill === "Freeze",
    switchWeaponDirection: input.switchWeaponDirection,
    switchWeaponIndex: input.switchWeaponIndex
  });
  const confirmedTarget = transition.castSkill !== null && input.pointerWorld ? cloneVec2(input.pointerWorld) : null;
  const castBlink = transition.castSkill === "Blink";
  const castFreeze = transition.castSkill === "Freeze";
  const suppressPrimaryHeld =
    input.castDash ||
    input.castCritical ||
    castBlink ||
    castFreeze ||
    transition.preparedSkill !== null ||
    transition.toggledPreparedSkill;

  return {
    input: {
      ...input,
      primaryHeld: suppressPrimaryHeld ? false : input.primaryHeld,
      primaryJustPressed: false,
      castBlink,
      castFreeze,
      pointerWorld: confirmedTarget ?? input.pointerWorld
    },
    preparedSkill: transition.preparedSkill,
    confirmedTarget
  };
}

function resolvePreparedSkillTransition(
  preparedSkill: PreparedSkill,
  input: PreparedSkillTransitionInput
): PreparedSkillTransition {
  let nextPreparedSkill = preparedSkill;
  const toggledPreparedSkill = input.toggleBlink || input.toggleFreeze;

  if (input.toggleBlink) {
    nextPreparedSkill = nextPreparedSkill === "Blink" ? null : "Blink";
  }
  if (input.toggleFreeze) {
    nextPreparedSkill = nextPreparedSkill === "Freeze" ? null : "Freeze";
  }

  const confirmedSkill = input.primaryJustPressed && !toggledPreparedSkill ? nextPreparedSkill : null;
  if (confirmedSkill !== null) {
    return {
      preparedSkill: null,
      castSkill: confirmedSkill,
      toggledPreparedSkill
    };
  }

  return {
    preparedSkill: nextPreparedSkill,
    castSkill: null,
    toggledPreparedSkill
  };
}

function toAuthoritativeInputSnapshot(
  command: PlayerCommand,
  fallback: AuthoritativeBattleInputSnapshot
): AuthoritativeBattleInputSnapshot {
  const hasCommandMovement = Math.hypot(command.movement.x, command.movement.y) > 0.0001;
  const hasFallbackMovement = Math.hypot(fallback.movement.x, fallback.movement.y) > 0.0001;
  const movement =
    hasCommandMovement || !hasFallbackMovement
      ? { x: command.movement.x, y: command.movement.y }
      : { x: fallback.movement.x, y: fallback.movement.y };

  return {
    movement,
    aim: { x: command.aim.x, y: command.aim.y },
    pointerWorld: { x: command.pointerWorld.x, y: command.pointerWorld.y },
    primaryHeld: command.primaryHeld || command.primaryJustPressed || fallback.primaryHeld,
    primaryJustPressed: command.primaryJustPressed || fallback.primaryJustPressed,
    sprint: command.sprint || fallback.sprint,
    reloadPressed: command.reloadPressed || fallback.reloadPressed,
    castDash: command.castDash || fallback.castDash,
    castCritical: command.castCritical || fallback.castCritical,
    castBlink: false,
    castFreeze: false,
    switchWeaponDirection: command.switchWeaponDirection,
    switchWeaponIndex: command.switchWeaponIndex ?? fallback.switchWeaponIndex
  };
}

function toFallbackAuthoritativeInputSnapshot(
  fallback: AuthoritativeBattleInputSnapshot
): AuthoritativeBattleInputSnapshot {
  return {
    ...fallback,
    castBlink: false,
    castFreeze: false,
    switchWeaponIndex: fallback.switchWeaponIndex
  };
}

function cloneVec2(value: Vec2): Vec2 {
  return { x: value.x, y: value.y };
}

function readNowMs(): number {
  return typeof performance !== "undefined" ? performance.now() : Date.now();
}

function recordAuthoritativePreparedInputDiagnostics(input: {
  preparedSkillBefore: PreparedSkill;
  primaryJustPressed: boolean;
  fallbackCastBlink: boolean;
  fallbackCastFreeze: boolean;
  runtimeToggleBlink: boolean;
  runtimeToggleFreeze: boolean;
  rawRuntimeToggleBlink: boolean;
  rawRuntimeToggleFreeze: boolean;
  runtimeToggleBlinkSuppressed: boolean;
  runtimeToggleFreezeSuppressed: boolean;
  preparedSkillAfter: PreparedSkill;
  castSkill: TargetedPreparedSkill | null;
  inputCastDash: boolean;
  inputCastCritical: boolean;
  outputCastBlink: boolean;
  outputCastFreeze: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
}): void {
  if (typeof window === "undefined") {
    return;
  }

  const root = ((window as unknown as {
    __slayDemoBattleDiagnostics?: {
      authoritativePreparedInput?: {
        samples?: unknown[];
      };
    };
  }).__slayDemoBattleDiagnostics ??= {});
  const diagnostics = (root.authoritativePreparedInput ??= {});
  const samples = (diagnostics.samples ??= []);
  samples.push({
    atMs: readNowMs(),
    ...input
  });
  if (samples.length > 400) {
    samples.splice(0, samples.length - 400);
  }
}
