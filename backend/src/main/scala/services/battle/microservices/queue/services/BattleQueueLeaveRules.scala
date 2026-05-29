package services.battle.microservices.queue.services

import services.battle.microservices.queue.objects.queue.*
import services.battle.objects.core.RoomId

private[battle] final case class BattleQueueLeaveTransition(
  rooms: Map[RoomId, QueueRoom],
  tickets: Map[TicketId, TicketRecord],
  queueRequests: Map[QueueRequestId, TicketId],
  outcome: BattleQueueLeaveOutcome
)

private[battle] object BattleQueueLeaveRules {
  /** 中文名：离开（leave）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def leave(
    rooms: Map[RoomId, QueueRoom],
    tickets: Map[TicketId, TicketRecord],
    queueRequests: Map[QueueRequestId, TicketId],
    ticketId: TicketId
  ): BattleQueueLeaveTransition =
    tickets.get(ticketId) match {
      case None =>
        BattleQueueLeaveTransition(
          rooms = rooms,
          tickets = tickets,
          queueRequests = queueRequests,
          outcome = BattleQueueLeaveOutcome.TicketNotFound
        )
      case Some(record) =>
        rooms.get(record.roomId) match {
          case Some(room) if !room.isWaiting =>
            BattleQueueLeaveTransition(
              rooms = rooms,
              tickets = tickets,
              queueRequests = queueRequests,
              outcome = BattleQueueLeaveOutcome.NotWaiting
            )
          case _ =>
            BattleQueueLeaveTransition(
              rooms = roomsAfterLeave(rooms, record.roomId, ticketId),
              tickets = tickets.removed(ticketId),
              queueRequests = queueRequestsAfterLeave(queueRequests, record),
              outcome = BattleQueueLeaveOutcome.LeftQueue
            )
        }
    }

  private def roomsAfterLeave(
    rooms: Map[RoomId, QueueRoom],
    roomId: RoomId,
    ticketId: TicketId
  ): Map[RoomId, QueueRoom] =
    rooms.get(roomId) match {
      case None =>
        rooms
      case Some(room) =>
        val updatedParticipants = room.participants.filterNot(_.ticketId == ticketId)
        (updatedParticipants.isEmpty, room.isWaiting) match {
          case (true, true) =>
            rooms.removed(roomId)
          case _ =>
            rooms.updated(roomId, room.copy(participants = updatedParticipants))
        }
    }

  private def queueRequestsAfterLeave(
    queueRequests: Map[QueueRequestId, TicketId],
    record: TicketRecord
  ): Map[QueueRequestId, TicketId] =
    record.queueRequestId match {
      case Some(id) => queueRequests.removed(id)
      case None     => queueRequests
    }
}
