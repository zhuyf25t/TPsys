package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

enum BattleQueueStatusError {
  case TicketNotFound
}

enum BattleRoomError {
  case MissingRoomId
  case RoomNotFound
}

enum BattleQueueLeaveOutcome {
  case LeftQueue
  case NotWaiting
  case TicketNotFound
}

trait BattleRoomLifecycleSink {
  def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit
}

object NoopBattleRoomLifecycleSink extends BattleRoomLifecycleSink {
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit = ()
}

trait BattleQueueService extends BattleSessionLookup with BattleRoomLifecycleSink {
  def join(command: BattleQueueJoinCommand): BattleQueueSnapshot
  def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot]
  def leave(ticketId: TicketId): BattleQueueLeaveOutcome
  def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot]
  def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot]
}

final case class BattleQueueJoinCommand(
  handle: PlayerHandle,
  sessionToken: SessionToken,
  queueRequestId: Option[QueueRequestId],
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
)

final case class RealtimeRoomHeartbeatCommand(
  roomId: Option[RoomId],
  ticketId: Option[TicketId],
  handle: Option[PlayerHandle]
)
