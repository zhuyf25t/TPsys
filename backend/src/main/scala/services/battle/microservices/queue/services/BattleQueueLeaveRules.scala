package services.battle.microservices.queue.services

import cats.effect.IO

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
  ): IO[BattleQueueLeaveTransition] =
    tickets.get(ticketId) match {
      case None =>
        leaveTransition(rooms, tickets, queueRequests, BattleQueueLeaveOutcome.TicketNotFound)
      case Some(record) =>
        rooms.get(record.roomId) match {
          case Some(room) =>
            room.isWaiting.flatMap {
              case false =>
                leaveTransition(rooms, tickets, queueRequests, BattleQueueLeaveOutcome.NotWaiting)
              case true =>
                leftQueueTransition(rooms, tickets, queueRequests, record, ticketId)
            }
          case None =>
            leftQueueTransition(rooms, tickets, queueRequests, record, ticketId)
        }
    }

  private def leftQueueTransition(
    rooms: Map[RoomId, QueueRoom],
    tickets: Map[TicketId, TicketRecord],
    queueRequests: Map[QueueRequestId, TicketId],
    record: TicketRecord,
    ticketId: TicketId
  ): IO[BattleQueueLeaveTransition] =
    for
      nextRooms <- roomsAfterLeave(rooms, record.roomId, ticketId)
      nextQueueRequests <- queueRequestsAfterLeave(queueRequests, record)
      transition <- leaveTransition(
        nextRooms,
        tickets.removed(ticketId),
        nextQueueRequests,
        BattleQueueLeaveOutcome.LeftQueue
      )
    yield transition

  private def leaveTransition(
    rooms: Map[RoomId, QueueRoom],
    tickets: Map[TicketId, TicketRecord],
    queueRequests: Map[QueueRequestId, TicketId],
    outcome: BattleQueueLeaveOutcome
  ): IO[BattleQueueLeaveTransition] =
    IO.pure(
      BattleQueueLeaveTransition(
        rooms = rooms,
        tickets = tickets,
        queueRequests = queueRequests,
        outcome = outcome
      )
    )

  private def roomsAfterLeave(
    rooms: Map[RoomId, QueueRoom],
    roomId: RoomId,
    ticketId: TicketId
  ): IO[Map[RoomId, QueueRoom]] =
    rooms.get(roomId) match {
      case None =>
        IO.pure(rooms)
      case Some(room) =>
        val updatedParticipants = room.participants.filterNot(_.ticketId == ticketId)
        room.isWaiting.map { waiting =>
          (updatedParticipants.isEmpty, waiting) match {
            case (true, true) =>
              rooms.removed(roomId)
            case _ =>
              rooms.updated(roomId, room.copy(participants = updatedParticipants))
          }
        }
    }

  private def queueRequestsAfterLeave(
    queueRequests: Map[QueueRequestId, TicketId],
    record: TicketRecord
  ): IO[Map[QueueRequestId, TicketId]] =
    IO.pure(record.queueRequestId match {
      case Some(id) => queueRequests.removed(id)
      case None     => queueRequests
    })
}
