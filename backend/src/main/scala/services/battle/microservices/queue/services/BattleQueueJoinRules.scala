package services.battle.microservices.queue.services

import services.battle.objects.queue.*

import services.battle.objects.BattleQueueJoinCommand
import services.battle.objects.core.{EpochMillis, PlayerId, QueueRequestId, TicketId}
import services.battle.objects.queue.BattleQueueParticipant

private[battle] final case class BattleQueueJoinDraft(
  room: QueueRoom,
  entry: QueueParticipantEntry,
  ticket: TicketRecord
)

private[battle] object BattleQueueJoinRules {
  /** 中文名：规范化命令（normalizeCommand）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def normalizeCommand(command: BattleQueueJoinCommand): BattleQueueJoinCommand =
    command.copy(handle = BattleQueueParticipantRules.normalizeHandle(command.handle))

  /** 中文名：draft（draft）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def draft(
    command: BattleQueueJoinCommand,
    room: QueueRoom,
    ticketId: TicketId,
    playerId: PlayerId,
    now: EpochMillis
  ): BattleQueueJoinDraft = {
    val participant = BattleQueueParticipant(
      playerId = playerId,
      handle = command.handle,
      joinedAt = now,
      lastSeen = now,
      rating = command.rating,
      avatar = command.avatar,
      skin = command.skin
    )
    val entry = QueueParticipantEntry(
      ticketId = ticketId,
      playerId = playerId,
      queueRequestId = command.queueRequestId,
      participant = participant
    )

    BattleQueueJoinDraft(
      room = room.copy(participants = room.participants :+ entry),
      entry = entry,
      ticket = TicketRecord(
        ticketId = ticketId,
        playerId = playerId,
        roomId = room.roomId,
        queueRequestId = command.queueRequestId
      )
    )
  }

  /** 中文名：队列requestsafter加入（queueRequestsAfterJoin）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def queueRequestsAfterJoin(
    queueRequests: Map[QueueRequestId, TicketId],
    command: BattleQueueJoinCommand,
    ticketId: TicketId
  ): Map[QueueRequestId, TicketId] =
    command.queueRequestId match {
      case Some(id) => queueRequests.updated(id, ticketId)
      case None     => queueRequests
    }
}
