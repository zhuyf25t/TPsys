package services.battle.microservices.runtime.services

import services.battle.microservices.actors.services.BattlePlayerRuntimeRules
import services.battle.microservices.combat.services.{BattleHeldFireRuntimeRules, BattleProjectileRuntimeRules, BattleWeaponFireRules, BattleWeaponRules}
import services.battle.database.abilities.BattlePickupRuleBook
import services.battle.database.runtime.BattleRuntimeRuleBook
import services.battle.objects.{BattleAggregateState, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}
import services.battle.objects.abilities.{BattlePickupRules, BattleSlowFieldRuntimeRules}
import services.battle.objects.runtime.BattleTimeRules

private[battle] object BattleRuntimeStepRules {
  /** advanceStateStep: advances one fixed authoritative battle tick. */
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
            tick = BattleTick(targetElapsed / BattleRuntimeRuleBook.runtime.tickStep.value)
          )
          val afterSlowFields = BattleSlowFieldRuntimeRules.advanceSlowFields(clockedState, deltaMs)
          val afterPlayers = BattlePlayerRuntimeRules.advancePlayers(afterSlowFields, deltaMs)
          val afterPickups = BattlePickupRules.advancePickups(afterPlayers, deltaMs)
          val afterRequestedReloads = BattleWeaponFireRules.resolveRequestedReloads(afterPickups)
          val afterHeldFire = BattleHeldFireRuntimeRules.resolveHeldPrimaryFire(afterRequestedReloads)
          val afterProjectiles = BattleProjectileRuntimeRules.advanceProjectiles(afterHeldFire, deltaMs)
          val afterCollected =
            BattlePickupRules.collectPickups(
              state = afterProjectiles,
              config = BattlePickupRuleBook.current,
              retainedBattleEventCount = BattleRuntimeRuleBook.history.retainedBattleEventCount.value,
              equipOrRefillWeapon = BattleWeaponRules.equipOrRefillWeapon
            )
          BattleRuntimeFinalizationRules.finalizeRuntimeStep(afterCollected, targetElapsed, now)
        }

      advancedRuntime.copy(serverTime = now)
    }
  }
}
