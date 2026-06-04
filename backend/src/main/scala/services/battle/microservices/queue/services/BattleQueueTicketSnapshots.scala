package services.battle.microservices.queue.services

import cats.effect.IO

import BattleQueueSnapshots.toQueueSnapshot
import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.core.{EpochMillis, RoomId}
import services.battle.microservices.queue.objects.queue.BattleQueueSnapshot

private[battle] object BattleQueueTicketSnapshots {
  /** 中文名：快照forticket（snapshotForTicket）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def snapshotForTicket(
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    ticketId: TicketId,
    now: EpochMillis
  ): IO[Option[BattleQueueSnapshot]] =
    for
      maybeRecord <- IO.pure(tickets.get(ticketId))
      maybeRoom <- IO.pure(
        maybeRecord match {
          case Some(record) => rooms.get(record.roomId)
          case None         => None
        }
      )
      maybeEntry <- IO.pure(
        maybeRoom match {
          case Some(room) => room.participants.find(_.ticketId == ticketId)
          case None       => None
        }
      )
      snapshot <- (maybeRoom, maybeEntry) match {
        case (Some(room), Some(entry)) =>
          toQueueSnapshot(room, entry, now).map(Some(_))
        case _ =>
          IO.pure(None)
      }
    yield snapshot

  /** 中文名：快照forwaitingticket（snapshotForWaitingTicket）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def snapshotForWaitingTicket(
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    ticketId: TicketId,
    now: EpochMillis
  ): IO[Option[BattleQueueSnapshot]] =
    for
      maybeRecord <- IO.pure(tickets.get(ticketId))
      maybeRoom <- maybeRecord match {
        case Some(record) =>
          rooms.get(record.roomId) match {
            case Some(room) =>
              room.isWaiting.map(waiting => Option.when(waiting)(room))
            case None =>
              IO.pure(None)
          }
        case None =>
          IO.pure(None)
        }
      maybeEntry <- IO.pure(
        maybeRoom match {
          case Some(room) => room.participants.find(_.ticketId == ticketId)
          case None       => None
        }
      )
      snapshot <- (maybeRoom, maybeEntry) match {
        case (Some(room), Some(entry)) =>
          toQueueSnapshot(room, entry, now).map(Some(_))
        case _ =>
          IO.pure(None)
      }
    yield snapshot
}
