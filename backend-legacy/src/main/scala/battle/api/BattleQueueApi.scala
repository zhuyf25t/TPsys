package slaydemo.backend.battle.api

final case class BattleQueueJoinRequest(
  handle: String,
  sessionToken: Option[String] = None,
  queueRequestId: Option[String] = None,
  rating: Option[Int] = None,
  avatar: Option[String] = None,
  skin: Option[String] = None
)

final case class RealtimeRoomHeartbeatRequest(
  ticketId: Option[String] = None,
  handle: Option[String] = None
)
