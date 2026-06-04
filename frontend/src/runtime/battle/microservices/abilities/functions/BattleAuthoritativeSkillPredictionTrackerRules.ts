import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction
} from "../../session/functions/BattleAuthoritativeLocalHeroReplayProjection";
import {
  findBlinkSkillState,
  findDashSkillState,
  getPredictedBlinkCooldownMs,
  getPredictedDashCooldownMs
} from "./BattleAuthoritativeSkillPredictionRules";

const PENDING_PREDICTION_TTL_MS = 900;
const BLINK_MISMATCH_ALLOWED_MS = 180;
const AUTHORITATIVE_PREDICTION_MATCH_DISTANCE = 48;

export interface BattlePendingBlinkPruneResult {
  pendingPrediction: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  clearLocalCooldown: boolean;
  clearLocalCooldownIfAuthoritativeReady: boolean;
}

export function createPendingBlinkPrediction(
  destination: Vec2,
  nowMs: number
): AuthoritativeLocalHeroPendingBlinkPrediction {
  return {
    destination: cloneVec2(destination),
    expiresAtMs: nowMs + PENDING_PREDICTION_TTL_MS,
    mismatchAllowedUntilMs: nowMs + BLINK_MISMATCH_ALLOWED_MS
  };
}

export function createPendingDashPrediction(
  destination: Vec2,
  nowMs: number
): AuthoritativeLocalHeroPendingDashPrediction {
  return {
    destination: cloneVec2(destination),
    expiresAtMs: nowMs + PENDING_PREDICTION_TTL_MS
  };
}

export function predictedBlinkCooldownUntilMs(nowMs: number): number {
  return nowMs + getPredictedBlinkCooldownMs();
}

export function predictedDashCooldownUntilMs(nowMs: number): number {
  return nowMs + getPredictedDashCooldownMs();
}

export function resolvePendingBlinkPrediction(
  pendingPrediction: AuthoritativeLocalHeroPendingBlinkPrediction | null,
  nowMs: number
): AuthoritativeLocalHeroPendingBlinkPrediction | null {
  return pendingPrediction && pendingPrediction.expiresAtMs >= nowMs ? pendingPrediction : null;
}

export function resolvePendingDashPrediction(
  pendingPrediction: AuthoritativeLocalHeroPendingDashPrediction | null,
  nowMs: number
): AuthoritativeLocalHeroPendingDashPrediction | null {
  return pendingPrediction && pendingPrediction.expiresAtMs >= nowMs ? pendingPrediction : null;
}

export function resolveEffectiveSkillCooldownMs(
  authoritativeCooldownMs: number,
  localCooldownUntilMs: number | null,
  nowMs: number
): number {
  const predictedCooldownMs = localCooldownUntilMs !== null ? Math.max(0, localCooldownUntilMs - nowMs) : 0;
  return Math.max(authoritativeCooldownMs, predictedCooldownMs);
}

export function authoritativeBlinkCooldownMs(player: Hero | null): number {
  return player ? findBlinkSkillState(player.skills)?.cooldownMs ?? 0 : 0;
}

export function authoritativeDashCooldownMs(player: Hero | null): number {
  return player ? findDashSkillState(player.skills)?.cooldownMs ?? 0 : 0;
}

export function prunePendingBlinkPrediction(
  pendingPrediction: AuthoritativeLocalHeroPendingBlinkPrediction | null,
  player: Hero | null,
  nowMs: number
): BattlePendingBlinkPruneResult {
  if (!pendingPrediction || pendingPrediction.expiresAtMs < nowMs) {
    return {
      pendingPrediction: null,
      clearLocalCooldown: false,
      clearLocalCooldownIfAuthoritativeReady: true
    };
  }

  if (!player || !player.alive) {
    return {
      pendingPrediction: null,
      clearLocalCooldown: true,
      clearLocalCooldownIfAuthoritativeReady: false
    };
  }

  if (distanceBetween(player.position, pendingPrediction.destination) <= AUTHORITATIVE_PREDICTION_MATCH_DISTANCE) {
    return {
      pendingPrediction: null,
      clearLocalCooldown: false,
      clearLocalCooldownIfAuthoritativeReady: false
    };
  }

  if (pendingPrediction.mismatchAllowedUntilMs < nowMs) {
    return {
      pendingPrediction: null,
      clearLocalCooldown: false,
      clearLocalCooldownIfAuthoritativeReady: true
    };
  }

  return {
    pendingPrediction,
    clearLocalCooldown: false,
    clearLocalCooldownIfAuthoritativeReady: false
  };
}

export function prunePendingDashPrediction(
  pendingPrediction: AuthoritativeLocalHeroPendingDashPrediction | null,
  player: Hero | null,
  nowMs: number
): AuthoritativeLocalHeroPendingDashPrediction | null {
  if (!pendingPrediction || pendingPrediction.expiresAtMs < nowMs || !player || !player.alive) {
    return null;
  }

  if (distanceBetween(player.position, pendingPrediction.destination) <= AUTHORITATIVE_PREDICTION_MATCH_DISTANCE) {
    return null;
  }

  return pendingPrediction;
}

export function syncLocalSkillCooldownUntilMs(
  localCooldownUntilMs: number | null,
  authoritativeCooldownMs: number,
  nowMs: number
): number | null {
  if (authoritativeCooldownMs > 0) {
    return Math.max(localCooldownUntilMs ?? 0, nowMs + authoritativeCooldownMs);
  }

  if (localCooldownUntilMs !== null && localCooldownUntilMs <= nowMs) {
    return null;
  }

  return localCooldownUntilMs;
}

export function clearLocalCooldownIfAuthoritativeReady(
  localCooldownUntilMs: number | null,
  authoritativeCooldownMs: number
): number | null {
  return authoritativeCooldownMs <= 0 ? null : localCooldownUntilMs;
}

function cloneVec2(value: Vec2): Vec2 {
  return { x: value.x, y: value.y };
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
