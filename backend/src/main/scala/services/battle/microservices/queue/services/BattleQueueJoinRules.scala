package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.core.{EpochMillis, PlayerId}

private[battle] final case class BattleQueueJoinDraft(
  room: QueueRoom,
  entry: QueueParticipantEntry,
  ticket: TicketRecord
)

private[battle] object BattleQueueJoinRules {
  /** 中文名：规范化命令（normalizeCommand）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def normalizeCommand(command: BattleQueueJoinCommand): IO[BattleQueueJoinCommand] =
    BattleQueueParticipantRules.normalizeHandle(command.handle).map(handle => command.copy(handle = handle))

  /** 中文名：draft（draft）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def draft(
    command: BattleQueueJoinCommand,
    room: QueueRoom,
    ticketId: TicketId,
    playerId: PlayerId,
    now: EpochMillis
  ): IO[BattleQueueJoinDraft] =
    for
      participant <- joinParticipant(command, playerId, now)
      entry <- participantEntry(command, ticketId, playerId, participant)
      ticket <- ticketRecord(command, room, ticketId, playerId)
      draft <- IO.pure(
        BattleQueueJoinDraft(
          room = room.copy(participants = room.participants :+ entry),
          entry = entry,
          ticket = ticket
        )
      )
    yield draft

  /** 中文名：队列requestsafter加入（queueRequestsAfterJoin）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def queueRequestsAfterJoin(
    queueRequests: Map[QueueRequestId, TicketId],
    command: BattleQueueJoinCommand,
    ticketId: TicketId
  ): IO[Map[QueueRequestId, TicketId]] =
    IO.pure(command.queueRequestId match {
      case Some(id) => queueRequests.updated(id, ticketId)
      case None     => queueRequests
    })

  private def joinParticipant(
    command: BattleQueueJoinCommand,
    playerId: PlayerId,
    now: EpochMillis
  ): IO[BattleQueueParticipant] =
    IO.pure(
      BattleQueueParticipant(
        playerId = playerId,
        handle = command.handle,
        joinedAt = now,
        lastSeen = now,
        rating = command.rating,
        avatar = command.avatar,
        skin = command.skin
      )
    )

  private def participantEntry(
    command: BattleQueueJoinCommand,
    ticketId: TicketId,
    playerId: PlayerId,
    participant: BattleQueueParticipant
  ): IO[QueueParticipantEntry] =
    IO.pure(
      QueueParticipantEntry(
        ticketId = ticketId,
        playerId = playerId,
        queueRequestId = command.queueRequestId,
        participant = participant
      )
    )

  private def ticketRecord(
    command: BattleQueueJoinCommand,
    room: QueueRoom,
    ticketId: TicketId,
    playerId: PlayerId
  ): IO[TicketRecord] =
    IO.pure(
      TicketRecord(
        ticketId = ticketId,
        playerId = playerId,
        roomId = room.roomId,
        queueRequestId = command.queueRequestId
      )
    )
}
