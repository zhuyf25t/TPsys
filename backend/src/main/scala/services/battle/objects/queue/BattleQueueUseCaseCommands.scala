package services.battle.objects.queue

import services.battle.objects.BattleMode
import services.battle.objects.core.{BattleAvatarKey, BattleSkinKey, QueueRequestId, Rating, RoomId, TicketId}
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

final case class RealtimeRoomHeartbeatCommand(
  roomId: Option[RoomId],
  ticketId: Option[TicketId],
  handle: Option[PlayerHandle]
)

final case class BattleRoomSnapshotQuery(roomId: RoomId)

enum BattleQueueLeaveOutcome {
  case LeftQueue
  case NotWaiting
  case TicketNotFound
}
