package services.battle.microservices.queue.objects.queue

import services.battle.objects.BattleMode
import services.battle.objects.core.RoomId
import services.battle.microservices.actors.objects.player.{BattleAvatarKey, BattleSkinKey, Rating}
import services.identity.objects.{PlayerHandle, SessionToken}

final case class BattleQueueJoinCommand(
  handle: PlayerHandle,
  sessionToken: SessionToken,
  battleMode: BattleMode,
  queueRequestId: Option[QueueRequestId],
  rating: Option[Rating],
  avatar: Option[BattleAvatarKey],
  skin: Option[BattleSkinKey]
)

final case class BattleQueueStatusQuery(ticketId: TicketId)

final case class BattleQueueLeaveCommand(ticketId: TicketId)

enum BattleRoomStartGateAction {
  case Keep
  case Pause
  case Resume
}

object BattleRoomStartGateAction {
  def fromWire(startPaused: Option[Boolean]): BattleRoomStartGateAction =
    startPaused match {
      case Some(true)  => BattleRoomStartGateAction.Pause
      case Some(false) => BattleRoomStartGateAction.Resume
      case None        => BattleRoomStartGateAction.Keep
    }
}

final case class RealtimeRoomHeartbeatCommand(
  roomId: Option[RoomId],
  ticketId: Option[TicketId],
  handle: Option[PlayerHandle],
  startGateAction: BattleRoomStartGateAction,
  chatMessage: Option[BattleRoomChatText]
)

final case class BattleRoomSnapshotQuery(roomId: RoomId)

enum BattleQueueLeaveOutcome {
  case LeftQueue
  case NotWaiting
  case TicketNotFound
}
