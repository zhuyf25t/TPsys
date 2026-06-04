import type { BattleSlowFieldState as SlowField } from "../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export const BATTLE_AUTHORITATIVE_LOCAL_HERO_REPLAY_SKIP_REASONS = [
  "invalid-input",
  "no-history",
  "no-unacked",
  "invalid-history"
] as const;

export type BattleAuthoritativeLocalHeroReplaySkipReason =
  typeof BATTLE_AUTHORITATIVE_LOCAL_HERO_REPLAY_SKIP_REASONS[number];

export interface AuthoritativeLocalHeroReplayCommandEntry {
  readonly clientCommandSeq: number;
  readonly createdAt: number;
  readonly command: {
    readonly clientCommandSeq: number;
    readonly movement: Vec2;
    readonly aim: Vec2;
    readonly pointerWorld?: Vec2 | null;
    readonly sprint: boolean;
    readonly castDash: boolean;
    readonly castBlink: boolean;
  };
}

export interface AuthoritativeLocalHeroReplayProjection {
  readonly position: Vec2;
  readonly stamina: number;
  readonly hasPredictedStamina: boolean;
}

export interface AuthoritativeLocalHeroPendingDashPrediction {
  readonly destination: Vec2;
  readonly expiresAtMs: number;
}

export interface AuthoritativeLocalHeroPendingBlinkPrediction {
  readonly destination: Vec2;
  readonly expiresAtMs: number;
  readonly mismatchAllowedUntilMs: number;
}

export interface BattleAuthoritativeLocalHeroReplayObstacleBounds {
  readonly position: Vec2;
  readonly size: Vec2;
  readonly shape?: BattleAuthoritativeLocalHeroReplayObstacleShape;
}

export type BattleAuthoritativeLocalHeroReplayObstacleShape =
  | { readonly kind: "aabb"; readonly size: Vec2 }
  | { readonly kind: "circle"; readonly radius: number };

export interface BattleAuthoritativeLocalHeroReplayConfig {
  readonly maxReplayCommandDeltaMs: number;
  readonly maxReplayTotalDeltaMs: number;
  readonly baseMoveSpeed: number;
  readonly heroMaxStamina: number;
  readonly sprintMultiplier: number;
  readonly staminaDrainPerSecond: number;
  readonly staminaRecoverPerSecond: number;
}

export interface BattleAuthoritativeLocalHeroReplayDiagnosticsRecordInput {
  readonly skipped: boolean;
  readonly skipReason: BattleAuthoritativeLocalHeroReplaySkipReason | null;
  readonly unacknowledgedCommandCount: number;
  readonly lastAcknowledgedCommandSeq: number | null;
  readonly highestLocalCommandSeq: number | null;
  readonly totalReplayDeltaMs: number;
  readonly clampedCommandCount: number;
  readonly rawAuthoritativePositionToReplayTargetDistance: number;
}

export interface BattleAuthoritativeLocalHeroReplayMotionInput {
  readonly position: Vec2;
  readonly direction: Vec2;
  readonly distance: number;
  readonly radius: number;
  readonly worldSize: Vec2;
  readonly obstacleBounds: readonly BattleAuthoritativeLocalHeroReplayObstacleBounds[];
}

export interface BattleAuthoritativeLocalHeroReplayMotionResult {
  readonly destination: Vec2;
}

export interface BattleAuthoritativeLocalHeroReplayBlinkPredictionInput {
  readonly player: Hero;
  readonly position: Vec2;
  readonly target: Vec2 | null | undefined;
  readonly worldSize: Vec2;
  readonly obstacleBounds: readonly BattleAuthoritativeLocalHeroReplayObstacleBounds[];
  readonly blinkCooldownMs?: number;
  readonly blinkActiveMs?: number;
}

export interface BattleAuthoritativeLocalHeroReplayDashPredictionInput {
  readonly position: Vec2;
  readonly movement: Vec2;
  readonly aim: Vec2;
  readonly radius: number;
  readonly worldSize: Vec2;
  readonly obstacleBounds: readonly BattleAuthoritativeLocalHeroReplayObstacleBounds[];
  readonly dashCooldownMs: number;
  readonly dashActiveMs?: number;
  readonly alive: boolean;
}

export interface BattleAuthoritativeLocalHeroReplayAbilityPrediction {
  readonly destination: Vec2;
}

export interface BattleAuthoritativeLocalHeroReplayDependencies {
  readonly isDiagnosticsEnabled: () => boolean;
  readonly recordDiagnostics: (input: BattleAuthoritativeLocalHeroReplayDiagnosticsRecordInput) => void;
  readonly resolveBlinkPrediction: (
    input: BattleAuthoritativeLocalHeroReplayBlinkPredictionInput
  ) => BattleAuthoritativeLocalHeroReplayAbilityPrediction | null;
  readonly resolveDashPrediction: (
    input: BattleAuthoritativeLocalHeroReplayDashPredictionInput
  ) => BattleAuthoritativeLocalHeroReplayAbilityPrediction | null;
  readonly isBlinkReady: (cooldownMs: number, activeMs: number) => boolean;
  readonly isDashReady: (cooldownMs: number, activeMs: number) => boolean;
  readonly getPredictedBlinkCooldownMs: () => number;
  readonly getPredictedDashCooldownMs: () => number;
  readonly resolveFreezeSpeedMultiplier: (position: Vec2, slowFields: readonly SlowField[]) => number;
  readonly findMotionDestination: (
    input: BattleAuthoritativeLocalHeroReplayMotionInput
  ) => BattleAuthoritativeLocalHeroReplayMotionResult;
}

export interface ResolveBattleAuthoritativeLocalHeroReplayProjectionInput {
  readonly authoritativePosition: Vec2;
  readonly worldSize: Vec2;
  readonly obstacleBounds: readonly BattleAuthoritativeLocalHeroReplayObstacleBounds[];
  readonly radius: number;
  readonly player: Hero;
  readonly stamina: number;
  readonly maxStamina?: number;
  readonly blinkCooldownMs?: number;
  readonly blinkActiveMs?: number;
  readonly dashCooldownMs?: number;
  readonly dashActiveMs?: number;
  readonly slowFields: readonly SlowField[];
  readonly commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  readonly lastClientCommandSeq: number;
  readonly nowMs: number;
  readonly pendingBlinkPrediction?: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  readonly pendingDashPrediction?: AuthoritativeLocalHeroPendingDashPrediction | null;
  readonly config: BattleAuthoritativeLocalHeroReplayConfig;
  readonly dependencies: BattleAuthoritativeLocalHeroReplayDependencies;
}

export function resolveBattleAuthoritativeLocalHeroReplayProjection({
  authoritativePosition,
  worldSize,
  obstacleBounds,
  radius,
  player,
  stamina,
  maxStamina,
  blinkCooldownMs,
  blinkActiveMs,
  dashCooldownMs,
  dashActiveMs,
  slowFields,
  commandHistory,
  lastClientCommandSeq,
  nowMs,
  pendingBlinkPrediction = null,
  pendingDashPrediction = null,
  config,
  dependencies
}: ResolveBattleAuthoritativeLocalHeroReplayProjectionInput): AuthoritativeLocalHeroReplayProjection {
  const diagnosticsEnabled = dependencies.isDiagnosticsEnabled();

  if (
    !isFinitePosition(authoritativePosition) ||
    !isFinitePosition(worldSize) ||
    !Number.isFinite(radius) ||
    radius <= 0 ||
    !Number.isFinite(stamina) ||
    !Number.isFinite(lastClientCommandSeq) ||
    !Number.isFinite(nowMs)
  ) {
    if (diagnosticsEnabled) {
      recordSkippedReplayDiagnostics({
        reason: "invalid-input",
        authoritativePosition,
        replayTarget: authoritativePosition,
        commandHistory,
        lastClientCommandSeq: null,
        unacknowledgedCommandCount: 0,
        dependencies
      });
    }

    return createSkippedReplayProjection(authoritativePosition, stamina);
  }

  const acknowledgedSeq = Math.max(0, Math.trunc(lastClientCommandSeq));
  const unacknowledgedCommands = commandHistory.filter((entry) => entry.clientCommandSeq > acknowledgedSeq);
  if (commandHistory.length === 0) {
    const replayTarget = resolvePendingPredictionTarget({
      replayedPosition: authoritativePosition,
      pendingPrediction: pendingBlinkPrediction,
      nowMs
    }) ?? resolvePendingPredictionTarget({
      replayedPosition: authoritativePosition,
      pendingPrediction: pendingDashPrediction,
      nowMs
    }) ?? authoritativePosition;
    if (diagnosticsEnabled) {
      recordSkippedReplayDiagnostics({
        reason: "no-history",
        authoritativePosition,
        replayTarget,
        commandHistory,
        lastClientCommandSeq: acknowledgedSeq,
        unacknowledgedCommandCount: 0,
        dependencies
      });
    }

    return createSkippedReplayProjection(replayTarget, stamina);
  }

  if (unacknowledgedCommands.length === 0) {
    const replayTarget = resolvePendingPredictionTarget({
      replayedPosition: authoritativePosition,
      pendingPrediction: pendingBlinkPrediction,
      nowMs
    }) ?? resolvePendingPredictionTarget({
      replayedPosition: authoritativePosition,
      pendingPrediction: pendingDashPrediction,
      nowMs
    }) ?? authoritativePosition;
    if (diagnosticsEnabled) {
      recordSkippedReplayDiagnostics({
        reason: "no-unacked",
        authoritativePosition,
        replayTarget,
        commandHistory,
        lastClientCommandSeq: acknowledgedSeq,
        unacknowledgedCommandCount: 0,
        dependencies
      });
    }

    return createSkippedReplayProjection(replayTarget, stamina);
  }

  if (unacknowledgedCommands.some((entry) => !isUsableHistoryEntry(entry))) {
    if (diagnosticsEnabled) {
      recordSkippedReplayDiagnostics({
        reason: "invalid-history",
        authoritativePosition,
        replayTarget: authoritativePosition,
        commandHistory,
        lastClientCommandSeq: acknowledgedSeq,
        unacknowledgedCommandCount: unacknowledgedCommands.length,
        dependencies
      });
    }

    return createSkippedReplayProjection(authoritativePosition, stamina);
  }

  let replayedPosition = clonePosition(authoritativePosition);
  const replayMaxStamina = resolveReplayMaxStamina(maxStamina, stamina, config.heroMaxStamina);
  let replayedStamina = clamp(stamina, 0, replayMaxStamina);
  let replayedBlinkCooldownMs = normalizeReplayTimer(blinkCooldownMs);
  let replayedBlinkActiveMs = normalizeReplayTimer(blinkActiveMs);
  let replayedDashCooldownMs = normalizeReplayTimer(dashCooldownMs);
  let replayedDashActiveMs = normalizeReplayTimer(dashActiveMs);
  let replayedDeltaMs = 0;
  let clampedCommandCount = 0;
  let hasPredictedStamina = false;
  let hasPredictedBlink = false;
  let hasPredictedDash = false;

  for (let index = 0; index < unacknowledgedCommands.length; index += 1) {
    const entry = unacknowledgedCommands[index];
    const nextEntry = unacknowledgedCommands[index + 1] ?? null;
    const commandEndMs = nextEntry ? nextEntry.createdAt : nowMs;
    const remainingReplayMs = config.maxReplayTotalDeltaMs - replayedDeltaMs;
    if (remainingReplayMs <= 0) {
      break;
    }

    const rawDeltaMs = commandEndMs - entry.createdAt;
    const deltaMs = clamp(rawDeltaMs, 0, Math.min(config.maxReplayCommandDeltaMs, remainingReplayMs));
    if (diagnosticsEnabled && deltaMs !== rawDeltaMs) {
      clampedCommandCount += 1;
    }
    replayedDeltaMs += deltaMs;

    if (deltaMs <= 0) {
      continue;
    }

    if (
      entry.command.castBlink &&
      replayedBlinkCooldownMs !== null &&
      dependencies.isBlinkReady(replayedBlinkCooldownMs, replayedBlinkActiveMs ?? 0)
    ) {
      const blinkPrediction = dependencies.resolveBlinkPrediction({
        player: {
          ...player,
          position: replayedPosition
        },
        position: replayedPosition,
        target: entry.command.pointerWorld,
        worldSize,
        obstacleBounds,
        blinkCooldownMs: replayedBlinkCooldownMs,
        blinkActiveMs: replayedBlinkActiveMs ?? 0
      });

      if (blinkPrediction) {
        replayedPosition = blinkPrediction.destination;
        replayedBlinkCooldownMs = dependencies.getPredictedBlinkCooldownMs();
        replayedBlinkActiveMs = 0;
        hasPredictedBlink = true;
      }
    }

    if (
      entry.command.castDash &&
      replayedDashCooldownMs !== null &&
      dependencies.isDashReady(replayedDashCooldownMs, replayedDashActiveMs ?? 0)
    ) {
      const dashPrediction = dependencies.resolveDashPrediction({
        position: replayedPosition,
        movement: entry.command.movement,
        aim: entry.command.aim,
        radius,
        worldSize,
        obstacleBounds,
        dashCooldownMs: replayedDashCooldownMs,
        dashActiveMs: replayedDashActiveMs ?? 0,
        alive: true
      });

      if (dashPrediction) {
        replayedPosition = dashPrediction.destination;
        replayedDashCooldownMs = dependencies.getPredictedDashCooldownMs();
        replayedDashActiveMs = 0;
        hasPredictedDash = true;
      }
    }

    const deltaSeconds = deltaMs / 1000;
    const movementActive = isMovementActive(entry.command.movement);
    hasPredictedStamina ||= entry.command.sprint && movementActive;
    const canSprint = entry.command.sprint && movementActive && replayedStamina > 0;
    replayedStamina = advanceReplayStamina({
      stamina: replayedStamina,
      maxStamina: replayMaxStamina,
      canSprint,
      deltaSeconds,
      config
    });

    const sprintMultiplier = canSprint ? config.sprintMultiplier : 1;
    const speedMultiplier = dependencies.resolveFreezeSpeedMultiplier(replayedPosition, slowFields);
    replayedPosition = dependencies.findMotionDestination({
      position: replayedPosition,
      direction: entry.command.movement,
      distance: config.baseMoveSpeed * sprintMultiplier * speedMultiplier * deltaSeconds,
      radius,
      worldSize,
      obstacleBounds
    }).destination;
    replayedBlinkCooldownMs = advanceReplayTimer(replayedBlinkCooldownMs, deltaMs);
    replayedBlinkActiveMs = advanceReplayTimer(replayedBlinkActiveMs, deltaMs);
    replayedDashCooldownMs = advanceReplayTimer(replayedDashCooldownMs, deltaMs);
    replayedDashActiveMs = advanceReplayTimer(replayedDashActiveMs, deltaMs);
  }

  if (!hasPredictedBlink) {
    replayedPosition =
      resolvePendingPredictionTarget({
        replayedPosition,
        pendingPrediction: pendingBlinkPrediction,
        nowMs
      }) ?? replayedPosition;
  }

  if (!hasPredictedDash) {
    replayedPosition =
      resolvePendingPredictionTarget({
        replayedPosition,
        pendingPrediction: pendingDashPrediction,
        nowMs
      }) ?? replayedPosition;
  }

  if (diagnosticsEnabled) {
    dependencies.recordDiagnostics({
      skipped: false,
      skipReason: null,
      unacknowledgedCommandCount: unacknowledgedCommands.length,
      lastAcknowledgedCommandSeq: acknowledgedSeq,
      highestLocalCommandSeq: resolveHighestLocalCommandSeq(commandHistory),
      totalReplayDeltaMs: replayedDeltaMs,
      clampedCommandCount,
      rawAuthoritativePositionToReplayTargetDistance: distanceBetween(authoritativePosition, replayedPosition)
    });
  }

  return {
    position: replayedPosition,
    stamina: replayedStamina,
    hasPredictedStamina
  };
}

function createSkippedReplayProjection(
  authoritativePosition: Vec2,
  authoritativeStamina: number
): AuthoritativeLocalHeroReplayProjection {
  return {
    position: clonePosition(authoritativePosition),
    stamina: authoritativeStamina,
    hasPredictedStamina: false
  };
}

function recordSkippedReplayDiagnostics({
  reason,
  authoritativePosition,
  replayTarget,
  commandHistory,
  lastClientCommandSeq,
  unacknowledgedCommandCount,
  dependencies
}: {
  reason: BattleAuthoritativeLocalHeroReplaySkipReason;
  authoritativePosition: Vec2;
  replayTarget: Vec2;
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number | null;
  unacknowledgedCommandCount: number;
  dependencies: BattleAuthoritativeLocalHeroReplayDependencies;
}): void {
  dependencies.recordDiagnostics({
    skipped: true,
    skipReason: reason,
    unacknowledgedCommandCount,
    lastAcknowledgedCommandSeq: lastClientCommandSeq,
    highestLocalCommandSeq: resolveHighestLocalCommandSeq(commandHistory),
    totalReplayDeltaMs: 0,
    clampedCommandCount: 0,
    rawAuthoritativePositionToReplayTargetDistance: distanceBetween(authoritativePosition, replayTarget)
  });
}

function isUsableHistoryEntry(entry: AuthoritativeLocalHeroReplayCommandEntry): boolean {
  return (
    Number.isFinite(entry.clientCommandSeq) &&
    Number.isFinite(entry.createdAt) &&
    Number.isFinite(entry.command.clientCommandSeq) &&
    isFinitePosition(entry.command.movement) &&
    isFinitePosition(entry.command.aim) &&
    (entry.command.pointerWorld === undefined ||
      entry.command.pointerWorld === null ||
      isFinitePosition(entry.command.pointerWorld)) &&
    typeof entry.command.castDash === "boolean" &&
    typeof entry.command.castBlink === "boolean"
  );
}

function isFinitePosition(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function isMovementActive(movement: Vec2): boolean {
  return Math.hypot(movement.x, movement.y) > 0.0001;
}

function resolveReplayMaxStamina(maxStamina: number | undefined, stamina: number, heroMaxStamina: number): number {
  if (maxStamina !== undefined && Number.isFinite(maxStamina)) {
    return Math.max(1, maxStamina);
  }

  return Math.max(1, heroMaxStamina, stamina);
}

function advanceReplayStamina({
  stamina,
  maxStamina,
  canSprint,
  deltaSeconds,
  config
}: {
  stamina: number;
  maxStamina: number;
  canSprint: boolean;
  deltaSeconds: number;
  config: BattleAuthoritativeLocalHeroReplayConfig;
}): number {
  if (canSprint) {
    return clamp(stamina - config.staminaDrainPerSecond * deltaSeconds, 0, maxStamina);
  }

  return clamp(stamina + config.staminaRecoverPerSecond * deltaSeconds, 0, maxStamina);
}

function normalizeReplayTimer(value: number | undefined): number | null {
  return value !== undefined && Number.isFinite(value) ? Math.max(0, value) : null;
}

function advanceReplayTimer(value: number | null, deltaMs: number): number | null {
  if (value === null) {
    return null;
  }

  return Math.max(0, value - Math.max(0, deltaMs));
}

function resolvePendingPredictionTarget({
  replayedPosition,
  pendingPrediction,
  nowMs
}: {
  replayedPosition: Vec2;
  pendingPrediction: AuthoritativeLocalHeroPendingDashPrediction | AuthoritativeLocalHeroPendingBlinkPrediction | null;
  nowMs: number;
}): Vec2 | null {
  if (
    pendingPrediction === null ||
    !Number.isFinite(pendingPrediction.expiresAtMs) ||
    pendingPrediction.expiresAtMs < nowMs ||
    !isFinitePosition(pendingPrediction.destination)
  ) {
    return null;
  }

  if (distanceBetween(replayedPosition, pendingPrediction.destination) <= 48) {
    return null;
  }

  return clonePosition(pendingPrediction.destination);
}

function clonePosition(position: Vec2): Vec2 {
  return { x: position.x, y: position.y };
}

function resolveHighestLocalCommandSeq(
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[]
): number | null {
  let highestSeq: number | null = null;
  for (const entry of commandHistory) {
    if (!Number.isFinite(entry.clientCommandSeq)) {
      continue;
    }

    const seq = Math.max(0, Math.trunc(entry.clientCommandSeq));
    highestSeq = highestSeq === null ? seq : Math.max(highestSeq, seq);
  }

  return highestSeq;
}

function distanceBetween(left: Vec2, right: Vec2): number {
  if (!isFinitePosition(left) || !isFinitePosition(right)) {
    return Number.NaN;
  }

  return Math.hypot(right.x - left.x, right.y - left.y);
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, max));
}
