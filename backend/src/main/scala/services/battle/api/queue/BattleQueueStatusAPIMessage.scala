package services.battle.api.queue

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.database.queue.{BattleQueueService, BattleQueueStatusError}
import services.battle.objects.{
  BattleAPIRequestError as BattleQueueStatusRequestDecodeError,
  BattleQueueSnapshot,
  BattleQueueStatusQuery
}
import services.battle.objects.apiTypes.queue.BattleQueueStatusRequest.given
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleQueueStatusAPIMessage(
  userId: UserId,
  query: BattleQueueStatusQuery
) extends APIWithTokenContextMessage[BattleQueueService, BattleQueueSnapshot] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[BattleQueueSnapshot] =
    for
      snapshot <- readSnapshot(queueService, query)
    yield snapshot

  private def readSnapshot(queueService: BattleQueueService, query: BattleQueueStatusQuery): IO[BattleQueueSnapshot] =
    for
      result <- IO.blocking(queueService.status(query.ticketId))
      snapshot <- statusResult(result)
    yield snapshot

  private def statusResult(result: Either[BattleQueueStatusError, BattleQueueSnapshot]): IO[BattleQueueSnapshot] =
    result match {
      case Right(snapshot) =>
        IO.pure(snapshot)
      case Left(error) =>
        statusError(error)
    }

  private def statusError(error: BattleQueueStatusError): IO[Nothing] =
    error match {
      case BattleQueueStatusError.TicketNotFound =>
        IO.raiseError(APIMessageError.NotFound("Queue ticket was not found."))
    }
}

object BattleQueueStatusAPIMessage {
  given Decoder[BattleQueueStatusAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequestValue(cursor.value)
          .left
          .map(error => DecodingFailure(BattleQueueStatusRequestDecodeError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleQueueStatusAPIMessage(userId, request)
    }

  private def decodeRequestValue(payload: Json): Either[BattleQueueStatusRequestDecodeError, BattleQueueStatusQuery] =
    payload.asObject match {
      case None =>
        Left(BattleQueueStatusRequestDecodeError.InvalidJsonObject)
      case Some(_) =>
        payload.as[BattleQueueStatusQuery].left.map(_ => BattleQueueStatusRequestDecodeError.MissingTicketId)
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == "missing_ticket_id" =>
        APIMessageError.BadRequest("ticketId is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle queue status request.")
    }
}
