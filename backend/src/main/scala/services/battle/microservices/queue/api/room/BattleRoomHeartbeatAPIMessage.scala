package services.battle.microservices.queue.api.room

import cats.effect.IO
import io.circe.{Decoder, Error}
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.queue.objects.queue.{
  BattleRoomChatText,
  TicketId,
  RealtimeRoomSnapshot
}
import services.battle.objects.core.RoomId
import services.identity.objects.PlayerHandle
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleRoomHeartbeatAPIMessage(
  userId: UserId,
  roomId: Option[RoomId],
  ticketId: Option[TicketId],
  handle: Option[PlayerHandle],
  startPaused: Option[Boolean],
  chatMessage: Option[BattleRoomChatText]
) extends APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshot] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[RealtimeRoomSnapshot] =
    BattleRoomHeartbeatAPIPlanner.plan(queueService, this)
}

object BattleRoomHeartbeatAPIMessage {
  import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageDecoding.given

  given Decoder[BattleRoomHeartbeatAPIMessage] =
    deriveDecoder[BattleRoomHeartbeatAPIMessage]

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleQueueAPIMessageErrors.roomHeartbeatDecodeFailure(error)
}
