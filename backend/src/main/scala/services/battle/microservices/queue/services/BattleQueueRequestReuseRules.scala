package services.battle.microservices.queue.services

import BattleQueueSnapshots.toQueueSnapshot
import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.BattleMode
import services.battle.objects.core.{EpochMillis, RoomId}
import services.battle.microservices.queue.objects.queue.BattleQueueSnapshot

private[battle] final case class BattleQueueRequestReuseResult(
  snapshot: Option[BattleQueueSnapshot],
  queueRequests: Map[QueueRequestId, TicketId]
)

private[battle] object BattleQueueRequestReuseRules {
  /** 中文名：reusewaiting请求（reuseWaitingRequest）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def reuseWaitingRequest(
    queueRequests: Map[QueueRequestId, TicketId],
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    queueRequestId: QueueRequestId,
    battleMode: BattleMode,
    now: EpochMillis
  ): BattleQueueRequestReuseResult =
    queueRequests.get(queueRequestId) match {
      case None =>
        BattleQueueRequestReuseResult(snapshot = None, queueRequests = queueRequests)
      case Some(ticketId) =>
        val snapshot = waitingSnapshot(tickets, rooms, ticketId, now).filter(_.battleMode == battleMode)
        val nextQueueRequests =
          if snapshot.isEmpty then queueRequests.removed(queueRequestId)
          else queueRequests
        BattleQueueRequestReuseResult(snapshot = snapshot, queueRequests = nextQueueRequests)
    }

  private def waitingSnapshot(
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    ticketId: TicketId,
    now: EpochMillis
  ): Option[BattleQueueSnapshot] =
    tickets.get(ticketId) match {
      case None =>
        None
      case Some(record) =>
        rooms.get(record.roomId).filter(_.isWaiting) match {
          case None =>
            None
          case Some(room) =>
            room.participants.find(_.ticketId == ticketId).map(entry => toQueueSnapshot(room, entry, now))
        }
    }
}
