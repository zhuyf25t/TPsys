package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleQueueSnapshots.toQueueSnapshot

private[services] object BattleQueueTicketSnapshots {
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
