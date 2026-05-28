package services.battle.microservices.queue.services

import services.battle.objects.queue.*

import services.battle.objects.queue.BattleQueueSnapshots.toQueueSnapshot
import services.battle.objects.core.{EpochMillis, RoomId, TicketId}
import services.battle.objects.queue.BattleQueueSnapshot

private[battle] object BattleQueueTicketSnapshots {
  /** 中文名：快照forticket（snapshotForTicket）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def snapshotForTicket(
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    ticketId: TicketId,
    now: EpochMillis
  ): Option[BattleQueueSnapshot] =
    for
      record <- tickets.get(ticketId)
      room <- rooms.get(record.roomId)
      entry <- room.participants.find(_.ticketId == ticketId)
    yield toQueueSnapshot(room, entry, now)

  /** 中文名：快照forwaitingticket（snapshotForWaitingTicket）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def snapshotForWaitingTicket(
    tickets: Map[TicketId, TicketRecord],
    rooms: Map[RoomId, QueueRoom],
    ticketId: TicketId,
    now: EpochMillis
  ): Option[BattleQueueSnapshot] =
    for
      record <- tickets.get(ticketId)
      room <- rooms.get(record.roomId)
      if room.isWaiting
      entry <- room.participants.find(_.ticketId == ticketId)
    yield toQueueSnapshot(room, entry, now)
}
