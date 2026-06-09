package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.core.{EpochMillis, RoomId}

private[battle] object BattleQueueHeartbeatRules {
  private val RetainedChatMessageCount = 40

  private final case class HeartbeatParticipantUpdate(
    participants: Vector[QueueParticipantEntry],
    actor: Option[QueueParticipantEntry]
  )

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
    for
      participantUpdate <- updateParticipants(room, request, now)
      touchedRoom <- IO.pure(room.copy(participants = participantUpdate.participants))
      gatedRoom <- updateStartGate(touchedRoom, request.startPaused, participantUpdate.actor, now)
      updatedRoom <- appendChatMessage(gatedRoom, request, participantUpdate.actor, now)
    yield updatedRoom

  private def updateParticipants(
    room: QueueRoom,
    request: RealtimeRoomHeartbeatCommand,
    now: EpochMillis
  ): IO[HeartbeatParticipantUpdate] =
    room.participants.foldLeft(IO.pure(HeartbeatParticipantUpdate(Vector.empty, None))) { (updateIO, entry) =>
      for
        update <- updateIO
        matches <- BattleQueueParticipantRules.heartbeatMatches(entry, request)
        nextEntry <-
          if matches then BattleQueueParticipantRules.touchHeartbeatParticipant(entry, now)
          else IO.pure(entry)
        nextActor = if matches then Some(nextEntry) else update.actor
      yield HeartbeatParticipantUpdate(update.participants :+ nextEntry, nextActor)
    }

  private def updateStartGate(
    room: QueueRoom,
    startPaused: Option[Boolean],
    actor: Option[QueueParticipantEntry],
    now: EpochMillis
  ): IO[QueueRoom] =
    (startPaused, actor) match {
      case (Some(true), Some(entry)) if room.isHost(entry)  => room.pauseStart(now)
      case (Some(false), Some(entry)) if room.isHost(entry) => room.resumeStart(now)
      case _                                                => IO.pure(room)
    }

  private def appendChatMessage(
    room: QueueRoom,
    request: RealtimeRoomHeartbeatCommand,
    actor: Option[QueueParticipantEntry],
    now: EpochMillis
  ): IO[QueueRoom] =
    (request.chatMessage, actor) match {
      case (Some(body), Some(entry)) =>
        val message = BattleRoomChatMessage(
          messageId = BattleRoomChatMessageId(s"${room.roomId.value}-${now.value}-${room.chatMessages.length}"),
          authorPlayerId = entry.playerId,
          authorHandle = entry.participant.handle,
          body = body,
          createdAt = now
        )
        IO.pure(room.copy(chatMessages = (room.chatMessages :+ message).takeRight(RetainedChatMessageCount)))
      case _ =>
        IO.pure(room)
    }
}
