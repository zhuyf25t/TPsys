import type { Hero, SlowField, Vec2 } from "../../objects/types";
import {
  BASE_MOVE_SPEED,
  HERO_MAX_STAMINA,
  SPRINT_MULTIPLIER,
  STAMINA_DRAIN_PER_SECOND,
  STAMINA_RECOVER_PER_SECOND
} from "../constants";
import { findMotionDestination, type MotionObstacleBounds } from "../../runtime/local/movement/motionController";
import { getFreezeSpeedMultiplier } from "../../runtime/local/skills/freezeFieldController";
import {
  getPredictedDashCooldownMs,
  isAuthoritativeLocalHeroDashReady,
  resolveAuthoritativeLocalHeroDashPrediction
} from "./authoritativeLocalHeroDashPrediction";
import {
  getPredictedBlinkCooldownMs,
  isAuthoritativeLocalHeroBlinkReady,
  resolveAuthoritativeLocalHeroBlinkPrediction
} from "./authoritativeLocalHeroBlinkPrediction";
import {
  recordAuthoritativeLocalHeroReplayDiagnostics,
  type AuthoritativeLocalHeroReplaySkipReason
} from "./authoritativeLocalHeroReplayDiagnostics";
import { isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";

const MAX_REPLAY_COMMAND_DELTA_MS = 100;
const MAX_REPLAY_TOTAL_DELTA_MS = 500;

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

export interface ResolveAuthoritativeLocalHeroReplayTargetInput {
  authoritativePosition: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  radius: number;
  player: Hero;
  stamina: number;
  maxStamina?: number;
  blinkCooldownMs?: number;
  blinkActiveMs?: number;
  dashCooldownMs?: number;
  dashActiveMs?: number;
  slowFields: readonly SlowField[];
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number;
  nowMs: number;
  pendingBlinkPrediction?: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  pendingDashPrediction?: AuthoritativeLocalHeroPendingDashPrediction | null;
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

/** 中文名：解析authoritative本地英雄回放目标（resolveAuthoritativeLocalHeroReplayTarget）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveAuthoritativeLocalHeroReplayTarget({
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
  pendingDashPrediction = null
}: ResolveAuthoritativeLocalHeroReplayTargetInput): AuthoritativeLocalHeroReplayProjection {
  const diagnosticsEnabled = isBattleDiagnosticsEnabled();

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
        unacknowledgedCommandCount: 0
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
        unacknowledgedCommandCount: 0
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
        unacknowledgedCommandCount: 0
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
        unacknowledgedCommandCount: unacknowledgedCommands.length
      });
    }

    return createSkippedReplayProjection(authoritativePosition, stamina);
  }

  let replayedPosition = clonePosition(authoritativePosition);
  const replayMaxStamina = resolveReplayMaxStamina(maxStamina, stamina);
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
    const remainingReplayMs = MAX_REPLAY_TOTAL_DELTA_MS - replayedDeltaMs;
    if (remainingReplayMs <= 0) {
      break;
    }

    const rawDeltaMs = commandEndMs - entry.createdAt;
    const deltaMs = clamp(rawDeltaMs, 0, Math.min(MAX_REPLAY_COMMAND_DELTA_MS, remainingReplayMs));
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
      isAuthoritativeLocalHeroBlinkReady(replayedBlinkCooldownMs, replayedBlinkActiveMs ?? 0)
    ) {
      const blinkPrediction = resolveAuthoritativeLocalHeroBlinkPrediction({
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
        replayedBlinkCooldownMs = getPredictedBlinkCooldownMs();
        replayedBlinkActiveMs = 0;
        hasPredictedBlink = true;
      }
    }

    if (
      entry.command.castDash &&
      replayedDashCooldownMs !== null &&
      isAuthoritativeLocalHeroDashReady(replayedDashCooldownMs, replayedDashActiveMs ?? 0)
    ) {
      const dashPrediction = resolveAuthoritativeLocalHeroDashPrediction({
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
        replayedDashCooldownMs = getPredictedDashCooldownMs();
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
      deltaSeconds
    });

    const sprintMultiplier = canSprint ? SPRINT_MULTIPLIER : 1;
    const speedMultiplier = getFreezeSpeedMultiplier(replayedPosition, slowFields);
    replayedPosition = findMotionDestination({
      position: replayedPosition,
      direction: entry.command.movement,
      distance: BASE_MOVE_SPEED * sprintMultiplier * speedMultiplier * deltaSeconds,
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
    recordAuthoritativeLocalHeroReplayDiagnostics({
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

function createSkippedReplayProjection(authoritativePosition: Vec2, authoritativeStamina: number): AuthoritativeLocalHeroReplayProjection {
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
  unacknowledgedCommandCount
}: {
  reason: AuthoritativeLocalHeroReplaySkipReason;
  authoritativePosition: Vec2;
  replayTarget: Vec2;
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number | null;
  unacknowledgedCommandCount: number;
}): void {
  recordAuthoritativeLocalHeroReplayDiagnostics({
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

function resolveReplayMaxStamina(maxStamina: number | undefined, stamina: number): number {
  if (maxStamina !== undefined && Number.isFinite(maxStamina)) {
    return Math.max(1, maxStamina);
  }

  return Math.max(1, HERO_MAX_STAMINA, stamina);
}

function advanceReplayStamina({
  stamina,
  maxStamina,
  canSprint,
  deltaSeconds
}: {
  stamina: number;
  maxStamina: number;
  canSprint: boolean;
  deltaSeconds: number;
}): number {
  if (canSprint) {
    return clamp(stamina - STAMINA_DRAIN_PER_SECOND * deltaSeconds, 0, maxStamina);
  }

  return clamp(stamina + STAMINA_RECOVER_PER_SECOND * deltaSeconds, 0, maxStamina);
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

function resolveHighestLocalCommandSeq(commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[]): number | null {
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
