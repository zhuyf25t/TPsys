package services.battle.microservices.queue.services

import cats.effect.IO

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
  ): IO[BattleQueueRequestReuseResult] =
    queueRequests.get(queueRequestId) match {
      case None =>
        IO.pure(BattleQueueRequestReuseResult(snapshot = None, queueRequests = queueRequests))
      case Some(ticketId) =>
        for
          waiting <- waitingSnapshot(tickets, rooms, ticketId, now)
          snapshot <- IO.pure(waiting.filter(_.battleMode == battleMode))
          nextQueueRequests <- IO.pure(
            if snapshot.isEmpty then queueRequests.removed(queueRequestId)
            else queueRequests
          )
        yield BattleQueueRequestReuseResult(snapshot = snapshot, queueRequests = nextQueueRequests)
    }

  private def waitingSnapshot(
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    ticketId: TicketId,
    now: EpochMillis
  ): IO[Option[BattleQueueSnapshot]] =
    tickets.get(ticketId) match {
      case None =>
        IO.pure(None)
      case Some(record) =>
        rooms.get(record.roomId) match {
          case None =>
            IO.pure(None)
          case Some(room) =>
            room.isWaiting.flatMap {
              case false =>
                IO.pure(None)
              case true =>
                room.participants.find(_.ticketId == ticketId) match {
                  case Some(entry) => toQueueSnapshot(room, entry, now).map(Some(_))
                  case None        => IO.pure(None)
                }
            }
        }
    }
}
