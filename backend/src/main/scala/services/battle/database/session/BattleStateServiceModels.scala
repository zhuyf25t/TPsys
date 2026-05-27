package services.battle.database.session

import services.battle.objects.command.BattleCommandAccepted
import services.battle.objects.core.{BattleAggregateState, EpochMillis, PlayerId, TicketId}
import services.battle.objects.result.BattleFinishProjectionStatus

private[battle] final case class StoredBattle(
  state: BattleAggregateState,
  commandOwnershipByPlayerId: Map[PlayerId, TicketId],
  finishProjectionStatus: BattleFinishProjectionStatus,
  lastUpdatedAt: EpochMillis,
  pendingStepMs: Long
)

private[services] final case class StateRead(
  result: Either[BattleStateReadError, BattleAggregateState],
  projectionCandidate: Option[BattleAggregateState]
)

private[services] final case class CommandSubmission(
  result: Either[BattleCommandSubmitError, BattleCommandAccepted],
  projectionCandidate: Option[BattleAggregateState]
)
