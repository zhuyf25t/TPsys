package services.battle.api.room

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.database.queue.{BattleQueueService, BattleRoomError}
import services.battle.objects.{
  BattleAPIRequestError as BattleRoomSnapshotRequestDecodeError,
  BattleRoomSnapshotQuery,
  RealtimeRoomSnapshot
}
import services.battle.objects.apiTypes.room.BattleRoomSnapshotRequest.given
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleRoomSnapshotAPIMessage(
  userId: UserId,
  query: BattleRoomSnapshotQuery
) extends APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshot] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[RealtimeRoomSnapshot] =
    for
      snapshot <- readSnapshot(queueService, query)
    yield snapshot

  private def readSnapshot(queueService: BattleQueueService, query: BattleRoomSnapshotQuery): IO[RealtimeRoomSnapshot] =
    for
      result <- IO.blocking(queueService.roomSnapshot(query.roomId))
      snapshot <- roomSnapshotResult(result)
    yield snapshot

  private def roomSnapshotResult(result: Either[BattleRoomError, RealtimeRoomSnapshot]): IO[RealtimeRoomSnapshot] =
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

object BattleRoomSnapshotAPIMessage {
  given Decoder[BattleRoomSnapshotAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequestValue(cursor.value)
          .left
          .map(error => DecodingFailure(BattleRoomSnapshotRequestDecodeError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleRoomSnapshotAPIMessage(userId, request)
    }

  private def decodeRequestValue(payload: Json): Either[BattleRoomSnapshotRequestDecodeError, BattleRoomSnapshotQuery] =
    payload.asObject match {
      case None =>
        Left(BattleRoomSnapshotRequestDecodeError.InvalidJsonObject)
      case Some(_) =>
        payload.as[BattleRoomSnapshotQuery].left.map(_ => BattleRoomSnapshotRequestDecodeError.MissingRoomId)
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == "missing_room_id" =>
        APIMessageError.BadRequest("roomId is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle room snapshot request.")
    }
}
