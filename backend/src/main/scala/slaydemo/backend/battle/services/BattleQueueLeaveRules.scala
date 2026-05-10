package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] final case class BattleQueueLeaveTransition(
  rooms: Map[RoomId, QueueRoom],
  tickets: Map[TicketId, TicketRecord],
  queueRequests: Map[QueueRequestId, TicketId],
  outcome: BattleQueueLeaveOutcome
)

private[services] object BattleQueueLeaveRules {
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
          case Some(room) if room.phase != MatchmakingRoomPhase.Waiting =>
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
        if updatedParticipants.isEmpty && room.phase == MatchmakingRoomPhase.Waiting then
          rooms.removed(roomId)
        else
          rooms.updated(roomId, room.copy(participants = updatedParticipants))
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
