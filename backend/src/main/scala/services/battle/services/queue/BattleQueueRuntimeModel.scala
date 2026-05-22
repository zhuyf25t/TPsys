package services.battle.services.queue

import services.battle.services.*

import services.battle.objects.*

private[services] final case class QueueRoom(
  roomId: RoomId,
  createdAt: EpochMillis,
  startsAt: EpochMillis,
  deadline: EpochMillis,
  participants: Vector[QueueParticipantEntry],
  capacity: BattleCapacity,
  durationMs: DurationMillis,
  phase: MatchmakingRoomPhase,
  finishedAt: Option[EpochMillis],
  battleSession: Option[BattleSessionDescriptor]
)

private[services] final case class QueueParticipantEntry(
  ticketId: TicketId,
  playerId: PlayerId,
  queueRequestId: Option[QueueRequestId],
  participant: BattleQueueParticipant
)

private[services] final case class TicketRecord(
  ticketId: TicketId,
  playerId: PlayerId,
  roomId: RoomId,
  queueRequestId: Option[QueueRequestId]
)

private[services] object BattleQueueSnapshots {
  /** 中文名：转为队列快照（toQueueSnapshot）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def toQueueSnapshot(
    room: QueueRoom,
    entry: QueueParticipantEntry,
    now: EpochMillis
  ): BattleQueueSnapshot =
    BattleQueueSnapshot(
      ticketId = entry.ticketId,
      playerId = entry.playerId,
      roomId = room.roomId,
      createdAt = entry.participant.joinedAt,
      startsAt = room.startsAt,
      deadline = room.deadline,
      serverTime = now,
      participants = room.participants.map(_.participant),
      capacity = room.capacity,
      durationMs = room.durationMs,
      phase = room.phase,
      finishedAt = room.finishedAt,
      battleSession = room.battleSession.map(_.copy(serverTime = now))
    )

  /** 中文名：转为房间快照（toRoomSnapshot）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def toRoomSnapshot(room: QueueRoom, now: EpochMillis): RealtimeRoomSnapshot =
    RealtimeRoomSnapshot(
      roomId = room.roomId,
      serverTime = now,
      participants = room.participants.map(_.participant),
      capacity = room.capacity,
      phase = room.phase,
      finishedAt = room.finishedAt,
      battleSession = room.battleSession.map(_.copy(serverTime = now))
    )
}
