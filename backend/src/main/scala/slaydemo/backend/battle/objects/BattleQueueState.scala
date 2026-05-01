package slaydemo.backend.battle.objects

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BattleQueueParticipant(
  playerId: PlayerId,
  handle: PlayerHandle,
  joinedAt: EpochMillis,
  lastSeen: EpochMillis,
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
)

final case class BattleSessionRosterEntry(
  seat: SeatIndex,
  playerId: PlayerId,
  handle: PlayerHandle,
  joinedAt: EpochMillis,
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
)

final case class BattleSessionBootstrapSeat(
  seat: SeatIndex,
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  joinedAt: EpochMillis,
  isBot: Boolean,
  spawnPointIndex: SpawnPointIndex,
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
)

final case class BattleSessionBootstrap(
  seats: Vector[BattleSessionBootstrapSeat]
)

final case class BattleSessionDescriptor(
  battleId: BattleId,
  startedAt: EpochMillis,
  serverTime: EpochMillis,
  roster: Vector[BattleSessionRosterEntry],
  capacity: BattleCapacity,
  bootstrap: Option[BattleSessionBootstrap]
)

final case class BattleQueueSnapshot(
  ticketId: TicketId,
  playerId: PlayerId,
  roomId: RoomId,
  createdAt: EpochMillis,
  startsAt: EpochMillis,
  deadline: EpochMillis,
  serverTime: EpochMillis,
  participants: Vector[BattleQueueParticipant],
  capacity: BattleCapacity,
  durationMs: DurationMillis,
  phase: MatchmakingRoomPhase,
  finishedAt: Option[EpochMillis],
  battleSession: Option[BattleSessionDescriptor]
)

final case class RealtimeRoomSnapshot(
  roomId: RoomId,
  serverTime: EpochMillis,
  participants: Vector[BattleQueueParticipant],
  capacity: BattleCapacity,
  phase: MatchmakingRoomPhase,
  finishedAt: Option[EpochMillis],
  battleSession: Option[BattleSessionDescriptor]
)
