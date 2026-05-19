package slaydemo.backend.battle.objects.queue

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.objects.core.*
import slaydemo.backend.battle.objects.event.*
import slaydemo.backend.battle.objects.pickup.*
import slaydemo.backend.battle.objects.player.*
import slaydemo.backend.battle.objects.projectile.*
import slaydemo.backend.battle.objects.queue.*
import slaydemo.backend.battle.objects.replay.*
import slaydemo.backend.battle.objects.result.*
import slaydemo.backend.battle.objects.skill.*
import slaydemo.backend.battle.objects.weapon.*

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
  participantKind: BattleParticipantKind,
  spawnPointIndex: SpawnPointIndex,
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
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
