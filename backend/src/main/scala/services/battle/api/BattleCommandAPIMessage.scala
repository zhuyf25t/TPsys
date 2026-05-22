package services.battle.api

import cats.effect.IO

import services.battle.objects.apiTypes.{
  BattleCommandAcceptedResponse,
  BattleCommandAPIRequest,
  BattleCommandAPIRequestError
}
import services.battle.services.BattleCommandSubmitError
import system.api.RegisteredAPIMessage

object BattleCommandAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      BattleCommandAPIRequest.decodeCommand(payload) match {
        case Left(error) =>
          requestError(error)
        case Right(command) =>
          IO.blocking(services.stateService.acceptCommand(command)).flatMap {
            case Right(accepted) =>
              BattleAPIMessageSupport.encode(BattleCommandAcceptedResponse.fromAccepted(accepted))
            case Left(error) =>
              submitError(error)
          }
      }
    }

  private def requestError(error: BattleCommandAPIRequestError): IO[Nothing] =
    error match {
      case BattleCommandAPIRequestError.MissingTicket =>
        BattleAPIMessageSupport.forbidden("command_not_authorized")
      case BattleCommandAPIRequestError.InvalidJsonObject =>
        BattleAPIMessageSupport.badRequest("Invalid battle command request.")
      case BattleCommandAPIRequestError.InvalidField(field) =>
        BattleAPIMessageSupport.badRequest(s"Invalid battle command field: $field")
    }

  private def submitError(error: BattleCommandSubmitError): IO[Nothing] =
    error match {
      case BattleCommandSubmitError.BattleNotFound =>
        BattleAPIMessageSupport.notFound("battle_not_found")
      case BattleCommandSubmitError.PlayerNotFound =>
        BattleAPIMessageSupport.badRequest("player_not_found")
      case BattleCommandSubmitError.BotCommandsNotSupported =>
        BattleAPIMessageSupport.badRequest("bot_commands_not_supported")
      case BattleCommandSubmitError.CommandNotAuthorized =>
        BattleAPIMessageSupport.forbidden("command_not_authorized")
    }
}
