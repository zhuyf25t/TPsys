package services.battle.application

import services.battle.application.*

import services.battle.objects.*

private[services] final case class BattleQueueRequestReuseResult(
  snapshot: Option[BattleQueueSnapshot],
  queueRequests: Map[QueueRequestId, TicketId]
)

private[services] object BattleQueueRequestReuseRules {
  /** 中文名：reusewaiting请求（reuseWaitingRequest）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
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
