package services.battle.microservices.runtime.services

import services.battle.database.runtime.BattleRuntimeRuleBook
import services.battle.objects.runtime.BattleReplayFrameRecorder
import services.battle.objects.{BattleAggregateState, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}

private[battle] object BattleRuntimeFinalizationRules {
  /** 中文名：完成运行时步骤（finalizeRuntimeStep）。游戏职责：在一�?tick 管线结束时更新阶段、时间、胜者和回放帧�?*/
  def finalizeRuntimeStep(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val phase =
      if BattleRuntimeFinishRules.isBattleFinished(state, elapsed) then BattlePhase.Finished else BattlePhase.Active
    if phase == BattlePhase.Finished then BattleRuntimeFinishRules.finishRuntimeState(state, elapsed, now)
    else {
      val activeState = state.copy(
        phase = BattlePhase.Active,
        serverTime = now,
        elapsedMs = ElapsedMillis(elapsed),
        tick = BattleTick(elapsed / BattleRuntimeRuleBook.runtime.tickStep.value),
        winnerPlayerId = None,
        winnerHeroId = None
      )
      activeState.copy(
        replayFrames = BattleReplayFrameRecorder.updateFrames(
          activeState.replayFrames,
          activeState.elapsedMs,
          activeState.players,
          activeState.projectiles,
          activeState.pickups,
          hasRuntimeEvents = activeState.events.exists(_.elapsedMs == activeState.elapsedMs),
          finished = false,
          replayFrameSampleInterval = BattleRuntimeRuleBook.history.replayFrameSampleInterval,
          retainedReplayFrameCount = BattleRuntimeRuleBook.history.retainedReplayFrameCount
        )
      )
    }
  }
}
