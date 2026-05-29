package services.battle.microservices.results.api

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.microservices.results.api.results.BattleResultRecordRequest.given
import services.battle.microservices.results.api.results.BattleResultRecordRequestDecodeError
import services.battle.microservices.results.api.results.BattleResultRecordResponse
import services.battle.microservices.results.objects.result.{BattleResultRecordCommand, BattleResultRecordValidationError}
import services.battle.microservices.results.services.BattleResultService
import system.api.{APIMessage, APIMessageError, APIWithTokenMessage}
import system.objects.UserId

final case class BattleResultRecordAPIMessage(
  userId: UserId,
  command: BattleResultRecordCommand
) extends APIWithTokenMessage[BattleResultRecordResponse] {
  override def plan(connection: Connection): IO[BattleResultRecordResponse] =
    for
      saved <- BattleResultService.record(connection, command).flatMap { result =>
        IO.fromEither(result.left.map(BattleResultRecordAPIMessage.validationApiError))
      }
    yield BattleResultRecordResponse.fromRecord(saved)
}

object BattleResultRecordAPIMessage {
  given Decoder[BattleResultRecordAPIMessage] =
    Decoder.instance { cursor =>
      for
        userId <- APIMessage.injectedUserIdValue(cursor.value).left.map(message => DecodingFailure(message, cursor.history))
        command <- decodeRequest(cursor.value)
          .left
          .map(error => DecodingFailure(BattleResultRecordRequestDecodeError.message(error), cursor.history))
      yield BattleResultRecordAPIMessage(userId, command)
    }

  private def validationApiError(error: BattleResultRecordValidationError): APIMessageError =
    error match {
      case BattleResultRecordValidationError.InvalidHandle =>
        APIMessageError.BadRequest("invalid_handle")
      case BattleResultRecordValidationError.VisitorNotAllowed =>
        APIMessageError.Forbidden("visitor_not_allowed")
    }

  private def decodeRequest(payload: Json): Either[BattleResultRecordRequestDecodeError, BattleResultRecordCommand] =
    payload.as[BattleResultRecordCommand].left.map(resultRecordDecodeError)

  private def resultRecordDecodeError(error: Error): BattleResultRecordRequestDecodeError =
    error match {
      case failure: DecodingFailure
          if failure.message == BattleResultRecordRequestDecodeError.message(BattleResultRecordRequestDecodeError.InvalidBattleId) =>
        BattleResultRecordRequestDecodeError.InvalidBattleId
      case failure: DecodingFailure
          if failure.message == BattleResultRecordRequestDecodeError.message(BattleResultRecordRequestDecodeError.InvalidHandle) =>
        BattleResultRecordRequestDecodeError.InvalidHandle
      case failure: DecodingFailure
          if failure.message == BattleResultRecordRequestDecodeError.message(BattleResultRecordRequestDecodeError.VisitorNotAllowed) =>
        BattleResultRecordRequestDecodeError.VisitorNotAllowed
      case _ =>
        BattleResultRecordRequestDecodeError.BadJson
    }

}
