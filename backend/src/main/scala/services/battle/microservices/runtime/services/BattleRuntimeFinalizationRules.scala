package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.microservices.runtime.services.BattleReplayFrameRecorder
import services.battle.objects.{BattleAggregateState, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}

private[battle] object BattleRuntimeFinalizationRules {
  def finalizeRuntimeStep(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    BattleRuntimeFinishRules.isBattleFinished(state, elapsed).flatMap { finished =>
      if finished then BattleRuntimeFinishRules.finishRuntimeState(state, elapsed, now, battleRules)
      else
        for
          runtimeRules <- battleRules.runtime
          historyRules <- battleRules.history
          activeState = state.copy(
            phase = BattlePhase.Active,
            serverTime = now,
            elapsedMs = ElapsedMillis(elapsed),
            tick = BattleTick(elapsed / runtimeRules.tickStep.value),
            winnerPlayerId = None,
            winnerHeroId = None
          )
          replayFrames <- BattleReplayFrameRecorder.updateFrames(
            activeState.replayFrames,
            activeState.elapsedMs,
            activeState.players,
            activeState.projectiles,
            activeState.pickups,
            hasRuntimeEvents = activeState.events.exists(_.elapsedMs == activeState.elapsedMs),
            finished = false,
            replayFrameSampleInterval = historyRules.replayFrameSampleInterval,
            retainedReplayFrameCount = historyRules.retainedReplayFrameCount
          )
        yield activeState.copy(replayFrames = replayFrames)
    }
}
