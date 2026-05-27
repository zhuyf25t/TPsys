package services.battle.api.room

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.database.queue.{BattleQueueService, BattleRoomError}
import services.battle.objects.{
  BattleAPIRequestError as BattleRoomHeartbeatRequestDecodeError,
  RealtimeRoomHeartbeatCommand,
  RealtimeRoomSnapshot
}
import services.battle.objects.apiTypes.room.BattleRoomHeartbeatRequest.given
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleRoomHeartbeatAPIMessage(
  userId: UserId,
  command: RealtimeRoomHeartbeatCommand
) extends APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshot] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[RealtimeRoomSnapshot] =
    for
      snapshot <- heartbeat(queueService, command)
    yield snapshot

  private def heartbeat(queueService: BattleQueueService, command: RealtimeRoomHeartbeatCommand): IO[RealtimeRoomSnapshot] =
    for
      result <- IO.blocking(queueService.heartbeat(command))
      snapshot <- heartbeatResult(result)
    yield snapshot

  private def heartbeatResult(result: Either[BattleRoomError, RealtimeRoomSnapshot]): IO[RealtimeRoomSnapshot] =
    result match {
      case Right(snapshot) =>
        IO.pure(snapshot)
      case Left(error) =>
        roomError(error)
    }

  private def roomError(error: BattleRoomError): IO[Nothing] =
    error match {
      case BattleRoomError.MissingRoomId =>
        IO.raiseError(APIMessageError.BadRequest("roomId is required."))
      case BattleRoomError.RoomNotFound =>
        IO.raiseError(APIMessageError.NotFound("Battle room was not found."))
    }
}

object BattleRoomHeartbeatAPIMessage {
  given Decoder[BattleRoomHeartbeatAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequestValue(cursor.value)
          .left
          .map(error => DecodingFailure(BattleRoomHeartbeatRequestDecodeError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleRoomHeartbeatAPIMessage(userId, request)
    }

  private def decodeRequestValue(payload: Json): Either[BattleRoomHeartbeatRequestDecodeError, RealtimeRoomHeartbeatCommand] =
    payload.asObject match {
      case None =>
        Left(BattleRoomHeartbeatRequestDecodeError.InvalidJsonObject)
      case Some(_) =>
        payload.as[RealtimeRoomHeartbeatCommand].left.map(_ => BattleRoomHeartbeatRequestDecodeError.InvalidJsonObject)
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle room heartbeat request.")
    }
}
