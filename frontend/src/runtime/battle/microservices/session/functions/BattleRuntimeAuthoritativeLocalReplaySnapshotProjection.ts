import type { BattleSlowFieldState as SlowField } from "../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleStateSkillResponseDto as SkillState } from "../../../../../objects/battle/microservices/session/api/state/BattleStatePlayerResponseApiTypes";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction,
  AuthoritativeLocalHeroReplayCommandEntry,
  AuthoritativeLocalHeroReplayProjection,
  BattleAuthoritativeLocalHeroReplayObstacleBounds
} from "./BattleAuthoritativeLocalHeroReplayProjection";

export interface BattleRuntimeAuthoritativeLocalReplayContext {
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number;
  nowMs: number;
  obstacleBounds: readonly BattleAuthoritativeLocalHeroReplayObstacleBounds[];
  pendingBlinkPrediction?: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  pendingDashPrediction?: AuthoritativeLocalHeroPendingDashPrediction | null;
  blinkCooldownMsOverride?: number;
  dashCooldownMsOverride?: number;
}

export interface BattleRuntimeAuthoritativeLocalReplayTargetInput {
  authoritativePosition: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly BattleAuthoritativeLocalHeroReplayObstacleBounds[];
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

export interface ResolveBattleRuntimeAuthoritativeLocalReplaySnapshotProjectionInput {
  authoritativePosition: Vec2;
  snapshot: GameSnapshot;
  heroRadius: number;
  authoritativeStamina: number;
  authoritativeMaxStamina: number;
  localHero: Hero;
  localPlayerReplay?: BattleRuntimeAuthoritativeLocalReplayContext;
  resolveReplayTarget(input: BattleRuntimeAuthoritativeLocalReplayTargetInput): AuthoritativeLocalHeroReplayProjection;
}

export function resolveBattleRuntimeAuthoritativeLocalReplaySnapshotProjection({
  authoritativePosition,
  snapshot,
  heroRadius,
  authoritativeStamina,
  authoritativeMaxStamina,
  localHero,
  localPlayerReplay,
  resolveReplayTarget
}: ResolveBattleRuntimeAuthoritativeLocalReplaySnapshotProjectionInput): AuthoritativeLocalHeroReplayProjection {
  if (!localPlayerReplay) {
    return createBattleRuntimeAuthoritativeOnlyReplayProjection(authoritativePosition, authoritativeStamina);
  }

  const authoritativeBlinkSkill = findAuthoritativeSkillState(localHero.skills, "Blink");
  const authoritativeDashSkill = findAuthoritativeSkillState(localHero.skills, "Dash");

  return resolveReplayTarget({
    authoritativePosition,
    worldSize: snapshot.worldSize,
    obstacleBounds: localPlayerReplay.obstacleBounds,
    radius: heroRadius,
    player: localHero,
    stamina: authoritativeStamina,
    maxStamina: authoritativeMaxStamina,
    blinkCooldownMs: resolveReplayCooldownMs(authoritativeBlinkSkill, localPlayerReplay.blinkCooldownMsOverride),
    blinkActiveMs: authoritativeBlinkSkill?.activeMs,
    dashCooldownMs: resolveReplayCooldownMs(authoritativeDashSkill, localPlayerReplay.dashCooldownMsOverride),
    dashActiveMs: authoritativeDashSkill?.activeMs,
    slowFields: snapshot.slowFields,
    commandHistory: localPlayerReplay.commandHistory,
    lastClientCommandSeq: localPlayerReplay.lastClientCommandSeq,
    nowMs: localPlayerReplay.nowMs,
    pendingBlinkPrediction: localPlayerReplay.pendingBlinkPrediction ?? null,
    pendingDashPrediction: localPlayerReplay.pendingDashPrediction ?? null
  });
}

function createBattleRuntimeAuthoritativeOnlyReplayProjection(
  authoritativePosition: Vec2,
  authoritativeStamina: number
): AuthoritativeLocalHeroReplayProjection {
  return {
    position: authoritativePosition,
    stamina: authoritativeStamina,
    hasPredictedStamina: false
  };
}

function findAuthoritativeSkillState(skills: readonly SkillState[], kind: SkillState["kind"]): SkillState | null {
  return skills.find((skill) => skill.kind === kind) ?? null;
}

function resolveReplayCooldownMs(
  authoritativeSkill: SkillState | null,
  cooldownMsOverride: number | undefined
): number | undefined {
  if (!Number.isFinite(cooldownMsOverride)) {
    return authoritativeSkill?.cooldownMs;
  }

  return Math.max(authoritativeSkill?.cooldownMs ?? 0, cooldownMsOverride ?? 0);
}
