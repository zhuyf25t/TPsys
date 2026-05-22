package services.battle.application

import services.battle.application.*

import services.battle.objects.*
import services.battle.application.BattleQueueSnapshots.toQueueSnapshot

private[services] object BattleQueueTicketSnapshots {
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
      if room.phase == MatchmakingRoomPhase.Waiting
      entry <- room.participants.find(_.ticketId == ticketId)
    yield toQueueSnapshot(room, entry, now)
}
