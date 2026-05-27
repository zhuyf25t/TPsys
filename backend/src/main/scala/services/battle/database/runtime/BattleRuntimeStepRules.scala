package services.battle.database.runtime

import services.battle.database.abilities.{BattlePickupRules, BattleSlowFieldRuntimeRules}
import services.battle.database.actors.BattlePlayerRuntimeRules
import services.battle.database.combat.{BattleHeldFireRuntimeRules, BattleProjectileRuntimeRules, BattleWeaponFireRules}
import services.battle.objects.{BattleAggregateState, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}

private[services] object BattleRuntimeStepRules {
  /** 中文名：推进状态步骤（advanceStateStep）。游戏职责：按固定 tick 管线推进权威战斗状态。 */
  def advanceStateStep(
    state: BattleAggregateState,
    requestedDeltaMs: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    if state.phase == BattlePhase.Finished then state.copy(serverTime = now)
    else {
      val targetElapsed = BattleTimeRules.elapsedAt(state.startedAt, state.durationMs, now)
      val previousElapsed = BattleTimeRules.elapsedAt(
        state.startedAt,
        state.durationMs,
        EpochMillis(now.value - math.max(0L, requestedDeltaMs))
      )
      val deltaMs = math.max(0L, targetElapsed - previousElapsed)
      val advancedRuntime =
        if deltaMs <= 0L then BattleRuntimeFinalizationRules.finalizeRuntimeStep(state, targetElapsed, now)
        else {
          val clockedState = state.copy(
            serverTime = now,
            elapsedMs = ElapsedMillis(targetElapsed),
            tick = BattleTick(targetElapsed / BattleRuntimeCatalog.TickStep.value)
          )
          val afterSlowFields = BattleSlowFieldRuntimeRules.advanceSlowFields(clockedState, deltaMs)
          val afterPlayers = BattlePlayerRuntimeRules.advancePlayers(afterSlowFields, deltaMs)
          val afterPickups = BattlePickupRules.advancePickups(afterPlayers, deltaMs)
          val afterRequestedReloads = BattleWeaponFireRules.resolveRequestedReloads(afterPickups)
          val afterHeldFire = BattleHeldFireRuntimeRules.resolveHeldPrimaryFire(afterRequestedReloads)
          val afterProjectiles = BattleProjectileRuntimeRules.advanceProjectiles(afterHeldFire, deltaMs)
          val afterCollected = BattlePickupRules.collectPickups(afterProjectiles)
          BattleRuntimeFinalizationRules.finalizeRuntimeStep(afterCollected, targetElapsed, now)
        }

      advancedRuntime.copy(serverTime = now)
    }
  }
}
