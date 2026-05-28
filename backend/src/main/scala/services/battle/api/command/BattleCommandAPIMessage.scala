package services.battle.api.command

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error}

import java.sql.Connection

import services.battle.microservices.session.services.{BattleCommandSubmitError, BattleStateService}
import services.battle.objects.{
  BattleAPIRequestError as BattleCommandAPIRequestError,
  BattleCommandRequestField
}
import services.battle.objects.apiTypes.command.BattleCommandRequestPayload
import services.battle.objects.command.{BattleCommandAccepted, BattleCommandRequest}
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleCommandAPIMessage(
  userId: UserId,
  command: BattleCommandRequest
) extends APIWithTokenContextMessage[BattleStateService, BattleCommandAccepted] {
  override def plan(stateService: BattleStateService, connection: Connection): IO[BattleCommandAccepted] =
    for
      accepted <- acceptCommand(stateService, command)
    yield accepted

  private def acceptCommand(stateService: BattleStateService, command: BattleCommandRequest): IO[BattleCommandAccepted] =
    IO.blocking(stateService.acceptCommand(command)).flatMap {
      case Right(accepted) =>
        IO.pure(accepted)
      case Left(error) =>
        submitError(error)
    }

  private def submitError(error: BattleCommandSubmitError): IO[Nothing] =
    error match {
      case BattleCommandSubmitError.BattleNotFound =>
        IO.raiseError(APIMessageError.NotFound("battle_not_found"))
      case BattleCommandSubmitError.PlayerNotFound =>
        IO.raiseError(APIMessageError.BadRequest("player_not_found"))
      case BattleCommandSubmitError.BotCommandsNotSupported =>
        IO.raiseError(APIMessageError.BadRequest("bot_commands_not_supported"))
      case BattleCommandSubmitError.CommandNotAuthorized =>
        IO.raiseError(APIMessageError.Forbidden("command_not_authorized"))
    }
}

object BattleCommandAPIMessage {
  given Decoder[BattleCommandAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequest(cursor.value)
          .left
          .map(error => DecodingFailure(BattleCommandAPIRequestError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleCommandAPIMessage(userId, request)
    }

  private def decodeRequest(payload: io.circe.Json): Either[BattleCommandAPIRequestError, BattleCommandRequest] =
    BattleCommandRequestPayload.decode(payload)

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == "missing_ticket" =>
        APIMessageError.Forbidden("command_not_authorized")
      case failure: DecodingFailure =>
        BattleCommandRequestField.fromDecoderMessage(failure.message) match {
          case Some(field) =>
            APIMessageError.BadRequest(s"Invalid battle command field: $field")
          case None =>
            APIMessageError.BadRequest("Invalid battle command request.")
        }
      case _ =>
        APIMessageError.BadRequest("Invalid battle command request.")
    }
}
