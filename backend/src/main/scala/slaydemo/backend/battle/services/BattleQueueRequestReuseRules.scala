package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] final case class BattleQueueRequestReuseResult(
  snapshot: Option[BattleQueueSnapshot],
  queueRequests: Map[QueueRequestId, TicketId]
)

private[services] object BattleQueueRequestReuseRules {
  def reuseWaitingRequest(
    queueRequests: Map[QueueRequestId, TicketId],
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    queueRequestId: QueueRequestId,
    now: EpochMillis
  ): BattleQueueRequestReuseResult =
    queueRequests.get(queueRequestId) match {
      case None =>
        BattleQueueRequestReuseResult(snapshot = None, queueRequests = queueRequests)
      case Some(ticketId) =>
        val snapshot = BattleQueueTicketSnapshots.snapshotForWaitingTicket(tickets, rooms, ticketId, now)
        val nextQueueRequests =
          if snapshot.isEmpty then queueRequests.removed(queueRequestId)
          else queueRequests
        BattleQueueRequestReuseResult(snapshot = snapshot, queueRequests = nextQueueRequests)
    }
}
