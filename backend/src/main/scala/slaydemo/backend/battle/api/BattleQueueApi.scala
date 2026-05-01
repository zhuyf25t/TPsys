package slaydemo.backend.battle.api

final case class RealtimeRoomHeartbeatRequest(
  roomId: Option[String],
  ticketId: Option[String],
  handle: Option[String]
)

final case class BattleQueueLeaveRequest(
  ticketId: String
)
