package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleHeldFireRuntimeRules.*
import slaydemo.backend.battle.services.BattlePickupRules.*
import slaydemo.backend.battle.services.BattlePlayerRuntimeRules.*
import slaydemo.backend.battle.services.BattleProjectileRuntimeRules.*
import slaydemo.backend.battle.services.BattleRuntimeFinalizationRules.*
import slaydemo.backend.battle.services.BattleSlowFieldRuntimeRules.*
import slaydemo.backend.battle.services.BattleTimeRules.*
import slaydemo.backend.battle.services.BattleWeaponFireRules.*

private[services] object BattleRuntimeStepRules {
  def advanceStateStep(
    state: BattleAggregateState,
    requestedDeltaMs: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    if state.phase == BattlePhase.Finished then state.copy(serverTime = now)
    else {
      val targetElapsed = elapsedAt(state.startedAt, state.durationMs, now)
      val previousElapsed = elapsedAt(state.startedAt, state.durationMs, EpochMillis(now.value - math.max(0L, requestedDeltaMs)))
      val deltaMs = math.max(0L, targetElapsed - previousElapsed)
      val advancedRuntime =
        if deltaMs <= 0L then finalizeRuntimeStep(state, targetElapsed, now)
        else
          val clockedState = state.copy(
            serverTime = now,
            elapsedMs = ElapsedMillis(targetElapsed),
            tick = BattleTick(targetElapsed / BattleRuntimeCatalog.TickStep.value)
          )
          val afterSlowFields = advanceSlowFields(clockedState, deltaMs)
          val afterPlayers = advancePlayers(afterSlowFields, deltaMs)
          val afterPickups = advancePickups(afterPlayers, deltaMs)
          val afterRequestedReloads = resolveRequestedReloads(afterPickups)
          val afterHeldFire = resolveHeldPrimaryFire(afterRequestedReloads)
          val afterProjectiles = advanceProjectiles(afterHeldFire, deltaMs)
          val afterCollected = collectPickups(afterProjectiles)
          finalizeRuntimeStep(afterCollected, targetElapsed, now)

      advancedRuntime.copy(serverTime = now)
    }
  }
}
