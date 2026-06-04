import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleVector2 as Vec2 } from "../../../objects/battle/objects/core/BattleCoreScalars";
import type { AuthoritativeBattleInputSnapshot } from "../input/authoritativeBattleInput";

export interface AuthoritativePreparedInputResolution {
  input: AuthoritativeBattleInputSnapshot;
  preparedSkill: PreparedSkill;
  confirmedTarget: Vec2 | null;
}

type TargetedPreparedSkill = Exclude<PreparedSkill, null>;

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
  const input = runtimeCommand
    ? toAuthoritativeInputSnapshot(runtimeCommand, fallback)
    : toFallbackAuthoritativeInputSnapshot(fallback);
  const transition = runtimeCommand
    ? resolvePreparedSkillTransition(preparedSkill, runtimeCommand)
    : {
        preparedSkill,
        castSkill: null,
        toggledPreparedSkill: false
      } satisfies PreparedSkillTransition;
  const confirmedTarget =
    runtimeCommand && transition.castSkill !== null ? cloneVec2(runtimeCommand.pointerWorld) : null;
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
  runtimeCommand: PlayerCommand
): PreparedSkillTransition {
  let nextPreparedSkill = preparedSkill;
  const toggledPreparedSkill = runtimeCommand.toggleBlink || runtimeCommand.toggleFreeze;

  if (runtimeCommand.toggleBlink) {
    nextPreparedSkill = nextPreparedSkill === "Blink" ? null : "Blink";
  }
  if (runtimeCommand.toggleFreeze) {
    nextPreparedSkill = nextPreparedSkill === "Freeze" ? null : "Freeze";
  }

  const confirmedSkill = runtimeCommand.primaryJustPressed
    ? resolveConfirmedPreparedSkill(runtimeCommand, nextPreparedSkill)
    : null;
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

function resolveConfirmedPreparedSkill(
  runtimeCommand: PlayerCommand,
  preparedSkill: PreparedSkill
): TargetedPreparedSkill | null {
  if (runtimeCommand.toggleFreeze) {
    return "Freeze";
  }
  if (runtimeCommand.toggleBlink) {
    return "Blink";
  }

  return preparedSkill;
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
    sprint: command.sprint || fallback.sprint,
    reloadPressed: command.reloadPressed || fallback.reloadPressed,
    castDash: command.castDash || fallback.castDash,
    castCritical: command.castCritical || fallback.castCritical,
    castBlink: false,
    castFreeze: false,
    switchWeaponDirection:
      command.switchWeaponDirection !== 0 ? command.switchWeaponDirection : fallback.switchWeaponDirection,
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
