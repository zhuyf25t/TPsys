package services.battle.objects

import services.identity.objects.{DisplayName, PlayerHandle, SessionToken}

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

final case class BattleStateReadQuery(battleId: BattleId)

enum BattleQueueLeaveOutcome {
  case LeftQueue
  case NotWaiting
  case TicketNotFound
}

final case class BattleResultRecordCommand(
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: String,
  durationMs: DurationMillis,
  score: Score,
  placement: Option[BattlePlacement],
  survivalOutcome: BattleSurvivalOutcome,
  ratingBefore: Rating,
  ratingDelta: RatingDelta,
  ratingAfter: Rating,
  resultLabel: BattleResultLabel,
  modeLabel: BattleModeLabel,
  mapLabel: BattleMapLabel,
  highlightLine: BattleHighlightLine,
  playersLine: BattlePlayersLine,
  timelineHint: BattleTimelineHint,
  currentLoadout: Option[String]
)

final case class BattleResultListQuery(
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: BattleResultListLimit
)
