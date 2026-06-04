package services.battle.microservices.session.services

import cats.effect.IO

import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.objects.core.{BattleAggregateState, DurationMillis, EpochMillis, PlayerId}
import services.battle.microservices.queue.objects.queue.TicketId
import services.battle.microservices.results.objects.result.BattleFinishProjectionStatus

private[battle] object BattleStoredBattleInitializationRules {
  def fromSeed(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[StoredBattle] =
    for
      initialState <- BattleSessionStateFactory.createInitialState(seed, battleDuration, now, battleRules)
      ownership <- commandOwnershipByPlayerId(seed)
      battle <- storedBattle(initialState, ownership)
    yield battle

  private def commandOwnershipByPlayerId(seed: BattleSessionSeed): IO[Map[PlayerId, TicketId]] =
    IO.pure(seed.commandOwnership.map(entry => entry.playerId -> entry.ticketId).toMap)

  private def storedBattle(
    initialState: BattleAggregateState,
    commandOwnershipByPlayerId: Map[PlayerId, TicketId]
  ): IO[StoredBattle] =
    IO.pure(
      StoredBattle(
        state = initialState,
        commandOwnershipByPlayerId = commandOwnershipByPlayerId,
        finishProjectionStatus = BattleFinishProjectionStatus.Pending,
        lastUpdatedAt = initialState.serverTime,
        pendingStepMs = 0L
      )
    )
}
