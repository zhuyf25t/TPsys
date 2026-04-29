package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandApi, BattleCommandRequest}
import slaydemo.backend.battle.objects.BattleAggregateState
import slaydemo.backend.battle.objects.BattleSessionDescriptor
import slaydemo.backend.shared.objects.{BattleId, UserId}

final case class BattleCommandOwnership(
  playerId: UserId,
  ticketId: String
)

final case class BattleRoomStatus(
  phase: String,
  finishedAt: Option[Long]
)

trait BattleService extends BattleCommandApi {
  def initializeRoomBattle(
    roomId: String,
    descriptor: BattleSessionDescriptor,
    commandOwnership: Seq[BattleCommandOwnership]
  ): BattleAggregateState
  def currentState(battleId: BattleId): Option[BattleAggregateState]
  def isResultReady(battleId: BattleId): Boolean
  def isReplayReady(battleId: BattleId): Boolean
  def roomStatus(roomId: String, now: Long): Option[BattleRoomStatus]
  def releaseRoom(roomId: String): Unit
}
