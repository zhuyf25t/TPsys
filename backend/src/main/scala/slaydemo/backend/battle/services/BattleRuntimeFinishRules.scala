package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattlePlayerLifecycleRules.*
import slaydemo.backend.battle.services.BattleReplayFrameRecorder.appendFrame

private[services] object BattleRuntimeFinishRules {
  def isBattleFinished(state: BattleAggregateState, elapsed: Long): Boolean =
    state.phase == BattlePhase.Finished ||
      elapsed >= state.durationMs.value ||
      state.players.count(player => player.alive && player.hp.value > 0) <= 1

  def finishRuntimeState(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val finishedPlayers = state.players.map(clearFinishedPlayerRuntime)
    val winner = winnerFor(finishedPlayers)
    state.copy(
      phase = BattlePhase.Finished,
      serverTime = now,
      elapsedMs = ElapsedMillis(elapsed),
      tick = BattleTick(elapsed / BattleRuntimeCatalog.TickStep.value),
      players = finishedPlayers,
      projectiles = Vector.empty,
      slowFields = state.slowFields,
      replayFrames = appendFrame(
        state.replayFrames,
        ElapsedMillis(elapsed),
        finishedPlayers,
        Vector.empty,
        state.pickups
      ),
      winnerPlayerId = winner.map(_.playerId),
      winnerHeroId = winner.map(_.heroId)
    )
  }

  def finishedAtForRoom(state: BattleAggregateState): EpochMillis =
    if state.elapsedMs.value >= state.durationMs.value then state.endsAt
    else state.serverTime
}
