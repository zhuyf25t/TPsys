package services.battle.microservices.runtime.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.actors.services.BattlePlayerLifecycleRules
import services.battle.microservices.extraction.services.BattleExtractionRuntimeRules
import services.battle.microservices.runtime.services.BattleReplayFrameRecorder
import services.battle.objects.{BattleAggregateState, BattleMapId, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}

private[battle] object BattleRuntimeFinishRules {
  private val WinterZombieMapId: BattleMapId = BattleMapId("winter-hunt-v1")

  def isBattleFinished(state: BattleAggregateState, elapsed: Long): IO[Boolean] =
    BattleExtractionRuntimeRules.hasExtracted(state).map { extracted =>
      state.phase == BattlePhase.Finished ||
        extracted ||
        elapsed >= state.durationMs.value ||
        survivorEndConditionReached(state)
    }

  private def survivorEndConditionReached(state: BattleAggregateState): Boolean =
    if state.mapId == WinterZombieMapId then
      val humans = state.players.filterNot(_.isBot)
      humans.nonEmpty && !humans.exists(player => player.alive && player.hp.value > 0)
    else
      state.players.count(player => player.alive && player.hp.value > 0) <= 1

  def finishRuntimeState(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    for
      finishedPlayers <- state.players.traverse(BattlePlayerLifecycleRules.clearFinishedPlayerRuntime)
      extractedWinner <- BattleExtractionRuntimeRules.extractedWinner(state)
      winner <- extractedWinner match {
        case Some(value) => IO.pure(Some(value))
        case None        => BattlePlayerLifecycleRules.winnerFor(finishedPlayers)
      }
      runtimeRules <- battleRules.runtime
      historyRules <- battleRules.history
      replayFrames <- BattleReplayFrameRecorder.appendFrame(
        state.replayFrames,
        ElapsedMillis(elapsed),
        finishedPlayers,
        Vector.empty,
        state.pickups,
        historyRules.retainedReplayFrameCount
      )
    yield state.copy(
      phase = BattlePhase.Finished,
      serverTime = now,
      elapsedMs = ElapsedMillis(elapsed),
      tick = BattleTick(elapsed / runtimeRules.tickStep.value),
      players = finishedPlayers,
      projectiles = Vector.empty,
      slowFields = state.slowFields,
      replayFrames = replayFrames,
      winnerPlayerId = winner.map(_.playerId),
      winnerHeroId = winner.map(_.heroId)
    )

  def finishedAtForRoom(state: BattleAggregateState, battleRules: BattleDynamicRuleBook): IO[EpochMillis] =
    IO.pure {
      if state.elapsedMs.value >= state.durationMs.value then state.endsAt
      else state.serverTime
    }
}
