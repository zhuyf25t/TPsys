package services.battle.engine


import services.battle.objects.*
import services.battle.engine.BattleReplayFrameRecorder.*
import services.battle.engine.BattleRuntimeFinishRules.*

private[services] object BattleRuntimeFinalizationRules {
  /** 中文名：finalizeruntimestep（finalizeRuntimeStep）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def finalizeRuntimeStep(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val phase = if isBattleFinished(state, elapsed) then BattlePhase.Finished else BattlePhase.Active
    if phase == BattlePhase.Finished then finishRuntimeState(state, elapsed, now)
    else
      val activeState = state.copy(
        phase = BattlePhase.Active,
        serverTime = now,
        elapsedMs = ElapsedMillis(elapsed),
        tick = BattleTick(elapsed / BattleRuntimeCatalog.TickStep.value),
        winnerPlayerId = None,
        winnerHeroId = None
      )
      activeState.copy(
        replayFrames = updateFrames(
          activeState.replayFrames,
          activeState.elapsedMs,
          activeState.players,
          activeState.projectiles,
          activeState.pickups,
          hasRuntimeEvents = activeState.events.exists(_.elapsedMs == activeState.elapsedMs),
          finished = false
        )
      )
  }
}
