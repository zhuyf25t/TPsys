import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction
} from "../../microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection";
import {
  authoritativeBlinkCooldownMs,
  authoritativeDashCooldownMs,
  clearLocalCooldownIfAuthoritativeReady,
  createPendingBlinkPrediction,
  createPendingDashPrediction,
  predictedBlinkCooldownUntilMs,
  predictedDashCooldownUntilMs,
  prunePendingBlinkPrediction,
  prunePendingDashPrediction,
  resolveEffectiveSkillCooldownMs,
  resolvePendingBlinkPrediction,
  resolvePendingDashPrediction,
  syncLocalSkillCooldownUntilMs
} from "../../microservices/abilities/functions/BattleAuthoritativeSkillPredictionTrackerRules";

export interface BattleAuthoritativeLocalSkillPredictionMotionResult {
  predictedBlinkDestination?: Vec2 | null;
  predictedDashDestination?: Vec2 | null;
}

export class BattleAuthoritativeLocalSkillPredictionTracker {
  private pendingLocalBlinkPrediction: AuthoritativeLocalHeroPendingBlinkPrediction | null = null;
  private pendingLocalDashPrediction: AuthoritativeLocalHeroPendingDashPrediction | null = null;
  private localBlinkCooldownUntilMs: number | null = null;
  private localDashCooldownUntilMs: number | null = null;

  public recordPredictedMotion(
    motionResult: BattleAuthoritativeLocalSkillPredictionMotionResult,
    nowMs: number
  ): void {
    if (motionResult.predictedBlinkDestination) {
      this.pendingLocalBlinkPrediction = createPendingBlinkPrediction(motionResult.predictedBlinkDestination, nowMs);
      this.localBlinkCooldownUntilMs = predictedBlinkCooldownUntilMs(nowMs);
    }

    if (motionResult.predictedDashDestination) {
      this.pendingLocalDashPrediction = createPendingDashPrediction(motionResult.predictedDashDestination, nowMs);
      this.localDashCooldownUntilMs = predictedDashCooldownUntilMs(nowMs);
    }
  }

  public resolvePendingBlinkPrediction(nowMs: number): AuthoritativeLocalHeroPendingBlinkPrediction | null {
    return resolvePendingBlinkPrediction(this.pendingLocalBlinkPrediction, nowMs);
  }

  public resolvePendingDashPrediction(nowMs: number): AuthoritativeLocalHeroPendingDashPrediction | null {
    return resolvePendingDashPrediction(this.pendingLocalDashPrediction, nowMs);
  }

  public resolveBlinkCooldownMs(player: Hero | null, nowMs: number): number {
    return resolveEffectiveSkillCooldownMs(
      authoritativeBlinkCooldownMs(player),
      this.localBlinkCooldownUntilMs,
      nowMs
    );
  }

  public resolveDashCooldownMs(player: Hero | null, nowMs: number): number {
    return resolveEffectiveSkillCooldownMs(authoritativeDashCooldownMs(player), this.localDashCooldownUntilMs, nowMs);
  }

  public syncFromAuthoritativeSnapshot(snapshot: GameSnapshot, nowMs: number): void {
    this.prunePendingBlinkPrediction(snapshot, nowMs);
    this.prunePendingDashPrediction(snapshot, nowMs);
    this.syncBlinkCooldown(snapshot, nowMs);
    this.syncDashCooldown(snapshot, nowMs);
  }

  private prunePendingBlinkPrediction(snapshot: GameSnapshot, nowMs: number): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    const pruneResult = prunePendingBlinkPrediction(this.pendingLocalBlinkPrediction, player ?? null, nowMs);
    this.pendingLocalBlinkPrediction = pruneResult.pendingPrediction;
    if (pruneResult.clearLocalCooldown) {
      this.localBlinkCooldownUntilMs = null;
    } else if (pruneResult.clearLocalCooldownIfAuthoritativeReady) {
      this.clearBlinkCooldownIfAuthoritativeReady(snapshot);
    }
  }

  private prunePendingDashPrediction(snapshot: GameSnapshot, nowMs: number): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    this.pendingLocalDashPrediction = prunePendingDashPrediction(this.pendingLocalDashPrediction, player ?? null, nowMs);
  }

  private syncBlinkCooldown(snapshot: GameSnapshot, nowMs: number): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    this.localBlinkCooldownUntilMs = syncLocalSkillCooldownUntilMs(
      this.localBlinkCooldownUntilMs,
      authoritativeBlinkCooldownMs(player ?? null),
      nowMs
    );
  }

  private syncDashCooldown(snapshot: GameSnapshot, nowMs: number): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    this.localDashCooldownUntilMs = syncLocalSkillCooldownUntilMs(
      this.localDashCooldownUntilMs,
      authoritativeDashCooldownMs(player ?? null),
      nowMs
    );
  }

  private clearBlinkCooldownIfAuthoritativeReady(snapshot: GameSnapshot): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    this.localBlinkCooldownUntilMs = clearLocalCooldownIfAuthoritativeReady(
      this.localBlinkCooldownUntilMs,
      authoritativeBlinkCooldownMs(player ?? null)
    );
  }
}
