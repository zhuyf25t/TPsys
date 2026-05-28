package services.battle.microservices.queue.services

import services.battle.objects.queue.*

import services.battle.objects.RealtimeRoomHeartbeatCommand
import services.battle.objects.core.{EpochMillis, RoomId, TicketId}

private[battle] object BattleQueueHeartbeatRules {
  /** 中文名：房间标识for心跳（roomIdForHeartbeat）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def roomIdForHeartbeat(
    tickets: Map[TicketId, TicketRecord],
    request: RealtimeRoomHeartbeatCommand
  ): Option[RoomId] =
    request.roomId.orElse(request.ticketId.flatMap(tickets.get).map(_.roomId))

  /** 中文名：更新心跳（updateHeartbeat）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
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
