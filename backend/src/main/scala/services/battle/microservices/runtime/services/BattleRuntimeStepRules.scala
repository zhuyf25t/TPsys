package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.microservices.actors.services.BattlePlayerRuntimeRules
import services.battle.microservices.combat.services.{BattleHeldFireRuntimeRules, BattleProjectileRuntimeRules, BattleWeaponFireRules, BattleWeaponRules}
import services.battle.microservices.extraction.services.BattleExtractionRuntimeRules
import services.battle.microservices.world.services.BattleArenaCatalog
import services.battle.objects.{BattleAggregateState, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}
import services.battle.microservices.abilities.services.{BattlePickupRules, BattleSlowFieldRuntimeRules}
import services.battle.microservices.runtime.services.BattleTimeRules

private[battle] object BattleRuntimeStepRules {
  /** advanceStateStep: advances one fixed authoritative battle tick. */
  def advanceStateStep(
    state: BattleAggregateState,
    requestedDeltaMs: Long,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] = {
    if state.phase == BattlePhase.Finished then IO.pure(state.copy(serverTime = now))
    else
      for
        targetElapsed <- BattleTimeRules.elapsedAt(state.startedAt, state.durationMs, now)
        previousElapsed <- BattleTimeRules.elapsedAt(
        state.startedAt,
        state.durationMs,
        EpochMillis(now.value - math.max(0L, requestedDeltaMs))
      )
        deltaMs = math.max(0L, targetElapsed - previousElapsed)
        advancedRuntime <-
          if deltaMs <= 0L then BattleRuntimeFinalizationRules.finalizeRuntimeStep(state, targetElapsed, now, battleRules)
          else
            for
              runtimeRules <- battleRules.runtime
              clockedState = state.copy(
                serverTime = now,
                elapsedMs = ElapsedMillis(targetElapsed),
                tick = BattleTick(targetElapsed / runtimeRules.tickStep.value)
              )
              afterSlowFields <- BattleSlowFieldRuntimeRules.advanceSlowFields(clockedState, deltaMs)
              afterPlayers <- BattlePlayerRuntimeRules.advancePlayers(afterSlowFields, deltaMs, battleRules)
              afterPickups <- BattlePickupRules.advancePickups(afterPlayers, deltaMs)
              afterRequestedReloads <- BattleWeaponFireRules.resolveRequestedReloads(afterPickups, battleRules)
              arena <- BattleArenaCatalog.contextFor(afterRequestedReloads.mapId, battleRules)
              afterHeldFire <- BattleHeldFireRuntimeRules.resolveHeldPrimaryFire(afterRequestedReloads, arena, battleRules)
              afterProjectiles <- BattleProjectileRuntimeRules.advanceProjectiles(afterHeldFire, deltaMs, arena, battleRules)
              pickupConfig <- battleRules.pickup
              historyRules <- battleRules.history
              afterCollected <-
                BattlePickupRules.collectPickups(
                  state = afterProjectiles,
                  config = pickupConfig,
                  retainedBattleEventCount = historyRules.retainedBattleEventCount.value,
                  equipOrRefillWeapon = (player, weaponKind) =>
                    BattleWeaponRules.equipOrRefillWeapon(player, weaponKind, battleRules)
                )
              afterObjectives <- BattleExtractionRuntimeRules.advanceObjectives(afterCollected, deltaMs, battleRules)
              finalized <- BattleRuntimeFinalizationRules.finalizeRuntimeStep(afterObjectives, targetElapsed, now, battleRules)
            yield finalized
      yield advancedRuntime.copy(serverTime = now)
  }
}
