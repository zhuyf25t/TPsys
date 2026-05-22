package services.battle.engine


import services.battle.objects.*
import services.battle.engine.BattleHeldFireRuntimeRules.*
import services.battle.engine.BattlePickupRules.*
import services.battle.engine.BattlePlayerRuntimeRules.*
import services.battle.engine.BattleProjectileRuntimeRules.*
import services.battle.engine.BattleRuntimeFinalizationRules.*
import services.battle.engine.BattleSlowFieldRuntimeRules.*
import services.battle.engine.BattleTimeRules.*
import services.battle.engine.BattleWeaponFireRules.*

private[services] object BattleRuntimeStepRules {
  /** 中文名：推进状态step（advanceStateStep）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
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
