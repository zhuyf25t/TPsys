package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.objects.BattleMode
import services.battle.objects.core.{
  DurationMillis,
  EpochMillis,
  PlayerId,
  RoomId
}
import services.battle.microservices.queue.objects.queue.{
  BattleCapacity,
  BattleQueueParticipant,
  BattleQueueSnapshot,
  BattleSessionDescriptor,
  MatchmakingRoomPhase,
  QueueRequestId,
  RealtimeRoomSnapshot,
  TicketId
}

private[battle] final case class QueueRuntimeState(
  rooms: Map[RoomId, QueueRoom],
  tickets: Map[TicketId, TicketRecord],
  queueRequests: Map[QueueRequestId, TicketId],
  idAllocator: BattleQueueIdAllocator
) {
  def withRooms(nextRooms: Map[RoomId, QueueRoom]): IO[QueueRuntimeState] =
    IO.pure(copy(rooms = nextRooms))

  def withRoom(room: QueueRoom): IO[QueueRuntimeState] =
    IO.pure(copy(rooms = rooms.updated(room.roomId, room)))

  def withTickets(nextTickets: Map[TicketId, TicketRecord]): IO[QueueRuntimeState] =
    IO.pure(copy(tickets = nextTickets))

  def withQueueRequests(nextQueueRequests: Map[QueueRequestId, TicketId]): IO[QueueRuntimeState] =
    IO.pure(copy(queueRequests = nextQueueRequests))
}

private[battle] object QueueRuntimeState {
  val initial: QueueRuntimeState =
    QueueRuntimeState(
      rooms = Map.empty,
      tickets = Map.empty,
      queueRequests = Map.empty,
      idAllocator = BattleQueueIdAllocator.initial
    )
}

private[battle] final case class QueueRoom(
  roomId: RoomId,
  battleMode: BattleMode,
  createdAt: EpochMillis,
  startsAt: EpochMillis,
  deadline: EpochMillis,
  participants: Vector[QueueParticipantEntry],
  capacity: BattleCapacity,
  durationMs: DurationMillis,
  lifecycle: QueueRoomLifecycle
) {
  def phase: IO[MatchmakingRoomPhase] =
    lifecycle.phase

  def finishedAt: IO[Option[EpochMillis]] =
    lifecycle.finishedAt

  def battleSession: IO[Option[BattleSessionDescriptor]] =
    lifecycle.battleSession

  def isWaiting: IO[Boolean] =
    IO.pure(lifecycle == QueueRoomLifecycle.Waiting)

  def markFinished(finishedAt: EpochMillis): IO[QueueRoom] =
    QueueRoomLifecycle.markFinished(lifecycle, finishedAt).map(nextLifecycle => copy(lifecycle = nextLifecycle))
}

private[battle] enum QueueRoomLifecycle {
  case Waiting
  case Active(session: BattleSessionDescriptor)
  case Finished(completedAt: EpochMillis, session: Option[BattleSessionDescriptor])

  def phase: IO[MatchmakingRoomPhase] =
    IO.pure(this match {
      case QueueRoomLifecycle.Waiting       => MatchmakingRoomPhase.Waiting
      case QueueRoomLifecycle.Active(_)     => MatchmakingRoomPhase.Active
      case QueueRoomLifecycle.Finished(_, _) => MatchmakingRoomPhase.Finished
    })

  def finishedAt: IO[Option[EpochMillis]] =
    IO.pure(this match {
      case QueueRoomLifecycle.Finished(value, _) => Some(value)
      case QueueRoomLifecycle.Waiting            => None
      case QueueRoomLifecycle.Active(_)          => None
    })

  def battleSession: IO[Option[BattleSessionDescriptor]] =
    IO.pure(this match {
      case QueueRoomLifecycle.Waiting             => None
      case QueueRoomLifecycle.Active(session)     => Some(session)
      case QueueRoomLifecycle.Finished(_, session) => session
    })
}

private[battle] object QueueRoomLifecycle {
  def markFinished(current: QueueRoomLifecycle, finishedAt: EpochMillis): IO[QueueRoomLifecycle] =
    current match {
      case QueueRoomLifecycle.Finished(existingFinishedAt, session) =>
        IO.pure(QueueRoomLifecycle.Finished(existingFinishedAt, session))
      case other =>
        other.battleSession.map(session => QueueRoomLifecycle.Finished(finishedAt, session))
    }
}

private[battle] enum QueueRoomStartDecision {
  case Start
  case Keep
}

private[battle] final case class QueueParticipantEntry(
  ticketId: TicketId,
  playerId: PlayerId,
  queueRequestId: Option[QueueRequestId],
  participant: BattleQueueParticipant
)

private[battle] final case class TicketRecord(
  ticketId: TicketId,
  playerId: PlayerId,
  roomId: RoomId,
  queueRequestId: Option[QueueRequestId]
)

private[battle] object BattleQueueSnapshots {
  /** 中文名：转为队列快照（toQueueSnapshot）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def toQueueSnapshot(
    room: QueueRoom,
    entry: QueueParticipantEntry,
    now: EpochMillis
  ): IO[BattleQueueSnapshot] =
    for
      phase <- room.phase
      finishedAt <- room.finishedAt
      battleSession <- room.battleSession
    yield BattleQueueSnapshot(
        ticketId = entry.ticketId,
        playerId = entry.playerId,
        roomId = room.roomId,
        battleMode = room.battleMode,
        createdAt = entry.participant.joinedAt,
        startsAt = room.startsAt,
        deadline = room.deadline,
        serverTime = now,
        participants = room.participants.map(_.participant),
        capacity = room.capacity,
        durationMs = room.durationMs,
        phase = phase,
        finishedAt = finishedAt,
        battleSession = battleSession.map(_.copy(serverTime = now))
      )

  /** 中文名：转为房间快照（toRoomSnapshot）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def toRoomSnapshot(room: QueueRoom, now: EpochMillis): IO[RealtimeRoomSnapshot] =
    for
      phase <- room.phase
      finishedAt <- room.finishedAt
      battleSession <- room.battleSession
    yield RealtimeRoomSnapshot(
      roomId = room.roomId,
      battleMode = room.battleMode,
      serverTime = now,
      participants = room.participants.map(_.participant),
      capacity = room.capacity,
      phase = phase,
      finishedAt = finishedAt,
      battleSession = battleSession.map(_.copy(serverTime = now))
    )
}
