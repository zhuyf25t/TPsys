package slaydemo.backend.battle.services.session

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.api.BattleCommandAccepted
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.results.BattleFinishProjectionStatus

private[services] final case class StoredBattle(
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
