package slaydemo.backend.battle.objects

final case class BattleQueueParticipant(
  playerId: String,
  handle: String,
  joinedAt: Long,
  lastSeen: Long,
  rating: Option[Int] = None,
  avatar: Option[String] = None,
  skin: Option[String] = None
)

final case class BattleSessionRosterEntry(
  seat: Int,
  playerId: String,
  handle: String,
  joinedAt: Long,
  rating: Option[Int] = None,
  avatar: Option[String] = None,
  skin: Option[String] = None
)

final case class BattleSessionBootstrapSeat(
  seat: Int,
  playerId: String,
  heroId: String,
  handle: String,
  displayName: String,
  joinedAt: Long,
  isBot: Boolean,
  spawnPointIndex: Int,
  rating: Option[Int] = None,
  avatar: Option[String] = None,
  skin: Option[String] = None
)

final case class BattleSessionBootstrap(
  seats: Seq[BattleSessionBootstrapSeat]
)

final case class BattleSessionDescriptor(
  battleId: String,
  startedAt: Long,
  serverTime: Long,
  roster: Seq[BattleSessionRosterEntry],
  capacity: Int,
  bootstrap: BattleSessionBootstrap
)

final case class BattleQueueSnapshot(
  ticketId: String,
  playerId: String,
  roomId: String,
  createdAt: Long,
  startsAt: Long,
  deadline: Long,
  participants: Seq[BattleQueueParticipant],
  capacity: Int,
  durationMs: Long,
  phase: String,
  finishedAt: Option[Long],
  battleSession: Option[BattleSessionDescriptor]
)

final case class RealtimeRoomSnapshot(
  roomId: String,
  serverTime: Long,
  participants: Seq[BattleQueueParticipant],
  capacity: Int,
  phase: String,
  finishedAt: Option[Long],
  battleSession: Option[BattleSessionDescriptor]
)
