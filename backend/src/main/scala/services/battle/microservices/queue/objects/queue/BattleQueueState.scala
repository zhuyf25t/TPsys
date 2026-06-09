package services.battle.microservices.queue.objects.queue

import services.battle.objects.BattleMode
import services.battle.objects.core.{
  BattleId,
  DurationMillis,
  EpochMillis,
  HeroId,
  PlayerId,
  RoomId,
  SeatIndex,
  SpawnPointIndex
}
import services.battle.microservices.actors.objects.player.{BattleAvatarKey, BattleParticipantKind, BattleSkinKey, Rating}
import services.identity.objects.{DisplayName, PlayerHandle}

final case class BattleRoomChatText private (value: String) extends AnyVal

object BattleRoomChatText {
  private val MaxLength = 160

  def fromWire(value: String): Option[BattleRoomChatText] =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(text => BattleRoomChatText(text.take(MaxLength)))
}

final case class BattleRoomChatMessage(
  messageId: BattleRoomChatMessageId,
  authorPlayerId: PlayerId,
  authorHandle: PlayerHandle,
  body: BattleRoomChatText,
  createdAt: EpochMillis
)

final case class BattleQueueParticipant(
  playerId: PlayerId,
  handle: PlayerHandle,
  joinedAt: EpochMillis,
  lastSeen: EpochMillis,
  rating: Option[Rating],
  avatar: Option[BattleAvatarKey],
  skin: Option[BattleSkinKey]
)

final case class BattleSessionRosterEntry(
  seat: SeatIndex,
  playerId: PlayerId,
  handle: PlayerHandle,
  joinedAt: EpochMillis,
  rating: Option[Rating],
  avatar: Option[BattleAvatarKey],
  skin: Option[BattleSkinKey]
)

final case class BattleSessionBootstrapSeat(
  seat: SeatIndex,
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  joinedAt: EpochMillis,
  participantKind: BattleParticipantKind,
  spawnPointIndex: SpawnPointIndex,
  rating: Option[Rating],
  avatar: Option[BattleAvatarKey],
  skin: Option[BattleSkinKey]
) {
  /**
   * 中文名：是否机器人（isBot）。
   * 游戏视线：等待区转入战局时，每个 seat 都需要告诉运行时这是人类玩家还是 bot，占位、AI 行为接管和结算展示都会用到这个判断。
   * 建模原因：对外仍暴露 Boolean 方便 JSON/前端读取，但真实来源是 `BattleParticipantKind` 枚举，避免在领域对象里散落裸布尔语义。
   */
  def isBot: Boolean =
    BattleParticipantKind.isBot(participantKind)
}

final case class BattleSessionBootstrap(
  seats: Vector[BattleSessionBootstrapSeat]
)

final case class BattleSessionDescriptor(
  battleId: BattleId,
  battleMode: BattleMode,
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
  battleMode: BattleMode,
  createdAt: EpochMillis,
  startsAt: EpochMillis,
  deadline: EpochMillis,
  serverTime: EpochMillis,
  participants: Vector[BattleQueueParticipant],
  capacity: BattleCapacity,
  durationMs: DurationMillis,
  phase: MatchmakingRoomPhase,
  startPaused: Boolean,
  pausedRemainingMs: Option[DurationMillis],
  chatMessages: Vector[BattleRoomChatMessage],
  finishedAt: Option[EpochMillis],
  battleSession: Option[BattleSessionDescriptor]
)

final case class RealtimeRoomSnapshot(
  roomId: RoomId,
  battleMode: BattleMode,
  startsAt: EpochMillis,
  deadline: EpochMillis,
  serverTime: EpochMillis,
  participants: Vector[BattleQueueParticipant],
  capacity: BattleCapacity,
  durationMs: DurationMillis,
  phase: MatchmakingRoomPhase,
  startPaused: Boolean,
  pausedRemainingMs: Option[DurationMillis],
  chatMessages: Vector[BattleRoomChatMessage],
  finishedAt: Option[EpochMillis],
  battleSession: Option[BattleSessionDescriptor]
)
