package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleQueueHeartbeatRules {
  def roomIdForHeartbeat(
    tickets: Map[TicketId, TicketRecord],
    request: RealtimeRoomHeartbeatCommand
  ): Option[RoomId] =
    request.roomId.orElse(request.ticketId.flatMap(tickets.get).map(_.roomId))

  def updateHeartbeat(
    room: QueueRoom,
    request: RealtimeRoomHeartbeatCommand,
    now: EpochMillis
  ): QueueRoom = {
    val participants = room.participants.map { entry =>
      if BattleQueueParticipantRules.heartbeatMatches(entry, request) then
        BattleQueueParticipantRules.touchHeartbeatParticipant(entry, now)
      else entry
    }

    room.copy(participants = participants)
  }
}
