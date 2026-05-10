package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleReplayFrameRecorder.*
import slaydemo.backend.battle.services.BattleRuntimeFinishRules.*

private[services] object BattleRuntimeFinalizationRules {
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
