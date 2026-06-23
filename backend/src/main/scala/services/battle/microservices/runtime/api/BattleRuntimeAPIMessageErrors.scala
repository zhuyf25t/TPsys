package services.battle.microservices.runtime.api

import cats.effect.IO
import io.circe.{DecodingFailure, Error}

import services.battle.microservices.runtime.objects.command.BattleCommandAccepted
import services.battle.microservices.session.services.{BattleCommandSubmitError, BattleStateReadError}
import services.battle.objects.core.BattleAggregateState
import system.api.APIMessageError

private[api] object BattleRuntimeAPIMessageErrors {
  def commandDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if isLoginRequired(failure) =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == BattleCommandRequestDecodeError.message(BattleCommandRequestDecodeError.MissingTicket) =>
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

  def commandCompatibilityDecodeFailure(error: BattleCommandRequestDecodeError): APIMessageError =
    error match {
      case BattleCommandRequestDecodeError.MissingTicket =>
        APIMessageError.Forbidden("command_not_authorized")
      case BattleCommandRequestDecodeError.InvalidJsonObject =>
        APIMessageError.BadRequest("invalid_battle_command_request")
      case BattleCommandRequestDecodeError.InvalidField(field) =>
        APIMessageError.BadRequest(s"invalid_battle_command_field_${field.toString}")
    }

  def stateReadDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if isLoginRequired(failure) =>
        APIMessageError.Unauthorized("Login is required.")
      case _ =>
        APIMessageError.BadRequest("battleId is required.")
    }

  def commandSubmit(result: Either[BattleCommandSubmitError, BattleCommandAccepted]): IO[BattleCommandAccepted] =
    result.fold(raiseCommandSubmit, IO.pure)

  def stateRead(result: Either[BattleStateReadError, BattleAggregateState]): IO[BattleAggregateState] =
    result.fold(raiseStateRead, IO.pure)

  private def isLoginRequired(failure: DecodingFailure): Boolean =
    failure.message == "Login is required."

  private def raiseCommandSubmit(error: BattleCommandSubmitError): IO[Nothing] =
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

  private def raiseStateRead(error: BattleStateReadError): IO[Nothing] =
    error match {
      case BattleStateReadError.BattleNotFound =>
        IO.raiseError(APIMessageError.NotFound("battle_not_found"))
    }
}
