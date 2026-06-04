import {
  BASE_MOVE_SPEED,
  HERO_MAX_STAMINA,
  SPRINT_MULTIPLIER,
  STAMINA_DRAIN_PER_SECOND,
  STAMINA_RECOVER_PER_SECOND
} from "../../objects/BattleGameConstants";
import {
  resolveBattleAuthoritativeLocalHeroReplayProjection,
  type AuthoritativeLocalHeroReplayProjection,
  type BattleAuthoritativeLocalHeroReplayConfig,
  type BattleAuthoritativeLocalHeroReplayDependencies
} from "../../../microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection";
import { findMotionDestination, type MotionObstacleBounds } from "../../../microservices/world/functions/BattleMotionRules";
import {
  getPredictedBlinkCooldownMs,
  getPredictedDashCooldownMs,
  isAuthoritativeLocalHeroBlinkReady,
  isAuthoritativeLocalHeroDashReady,
  resolveAuthoritativeLocalHeroBlinkPrediction,
  resolveAuthoritativeLocalHeroDashPrediction
} from "../../../microservices/abilities/functions/BattleAuthoritativeSkillPredictionRules";
import { getFreezeSpeedMultiplier } from "../../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";
import { recordAuthoritativeLocalHeroReplayDiagnostics } from "../diagnostics/authoritativeLocalHeroReplayDiagnostics";
import { isBattleDiagnosticsEnabled } from "../diagnostics/battleDiagnosticsGate";
import type { ResolveAuthoritativeLocalHeroReplayTargetInput } from "./objects/BattleAuthoritativeLocalHeroReplayObjects";

export type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction,
  AuthoritativeLocalHeroReplayCommandEntry,
  AuthoritativeLocalHeroReplayProjection
} from "../../../microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection";

export type { ResolveAuthoritativeLocalHeroReplayTargetInput } from "./objects/BattleAuthoritativeLocalHeroReplayObjects";

const AUTHORITATIVE_LOCAL_HERO_REPLAY_CONFIG: BattleAuthoritativeLocalHeroReplayConfig = {
  maxReplayCommandDeltaMs: 100,
  maxReplayTotalDeltaMs: 500,
  baseMoveSpeed: BASE_MOVE_SPEED,
  heroMaxStamina: HERO_MAX_STAMINA,
  sprintMultiplier: SPRINT_MULTIPLIER,
  staminaDrainPerSecond: STAMINA_DRAIN_PER_SECOND,
  staminaRecoverPerSecond: STAMINA_RECOVER_PER_SECOND
};

const RENDERER_AUTHORITATIVE_LOCAL_HERO_REPLAY_DEPENDENCIES: BattleAuthoritativeLocalHeroReplayDependencies = {
  isDiagnosticsEnabled: isBattleDiagnosticsEnabled,
  recordDiagnostics: recordAuthoritativeLocalHeroReplayDiagnostics,
  resolveBlinkPrediction: (input) => resolveAuthoritativeLocalHeroBlinkPrediction({
    ...input,
    obstacleBounds: input.obstacleBounds as readonly MotionObstacleBounds[]
  }),
  resolveDashPrediction: (input) => resolveAuthoritativeLocalHeroDashPrediction({
    ...input,
    obstacleBounds: input.obstacleBounds as readonly MotionObstacleBounds[]
  }),
  isBlinkReady: isAuthoritativeLocalHeroBlinkReady,
  isDashReady: isAuthoritativeLocalHeroDashReady,
  getPredictedBlinkCooldownMs,
  getPredictedDashCooldownMs,
  resolveFreezeSpeedMultiplier: getFreezeSpeedMultiplier,
  findMotionDestination: (input) => findMotionDestination({
    ...input,
    obstacleBounds: input.obstacleBounds as readonly MotionObstacleBounds[]
  })
};

export function resolveAuthoritativeLocalHeroReplayTarget(
  input: ResolveAuthoritativeLocalHeroReplayTargetInput
): AuthoritativeLocalHeroReplayProjection {
  return resolveBattleAuthoritativeLocalHeroReplayProjection({
    ...input,
    config: AUTHORITATIVE_LOCAL_HERO_REPLAY_CONFIG,
    dependencies: RENDERER_AUTHORITATIVE_LOCAL_HERO_REPLAY_DEPENDENCIES
  });
}
