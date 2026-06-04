package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.core.{EpochMillis, RoomId}

private[battle] object BattleQueueHeartbeatRules {
  /** 中文名：房间标识for心跳（roomIdForHeartbeat）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def roomIdForHeartbeat(
    tickets: Map[TicketId, TicketRecord],
    request: RealtimeRoomHeartbeatCommand
  ): IO[Option[RoomId]] =
    IO.pure(request.roomId.orElse(request.ticketId.flatMap(tickets.get).map(_.roomId)))

  /** 中文名：更新心跳（updateHeartbeat）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def updateHeartbeat(
    room: QueueRoom,
    request: RealtimeRoomHeartbeatCommand,
    now: EpochMillis
  ): IO[QueueRoom] =
    room.participants.foldLeft(IO.pure(Vector.empty[QueueParticipantEntry])) { (participantsIO, entry) =>
      for
        participants <- participantsIO
        matches <- BattleQueueParticipantRules.heartbeatMatches(entry, request)
        nextEntry <-
          if matches then BattleQueueParticipantRules.touchHeartbeatParticipant(entry, now)
          else IO.pure(entry)
      yield participants :+ nextEntry
    }.map(participants => room.copy(participants = participants))
}
