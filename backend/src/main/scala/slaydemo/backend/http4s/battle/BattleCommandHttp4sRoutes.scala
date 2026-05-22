package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request, Status}

import slaydemo.backend.battle.objects.BattleCommandRequest
import slaydemo.backend.battle.objects.apiTypes.{
  BattleCommandAcceptedResponse,
  BattleCommandAPIRequest,
  BattleCommandAPIRequestError,
  BattleCommandRequestField,
  BattleCommandRequestTarget
}
import slaydemo.backend.battle.services.{BattleCommandSubmitError, BattleStateService}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, decodeJsonObjectBody, errorResponse, jsonOk, requestPath}

private[http4s] object BattleCommandHttp4sRoutes {
  private val InvalidJsonObjectError =
    apiError(
      Status.BadRequest,
      "bad_request",
      "Request body must be a JSON object with supported primitive or object fields."
    )
  private val MethodNotAllowedError =
    apiError(
      Status.MethodNotAllowed,
      "method_not_allowed",
      "Only POST and OPTIONS are supported."
    )
  private val CommandNotAuthorizedError =
    apiError(
      Status.Forbidden,
      "command_not_authorized",
      "command_not_authorized"
    )
  private val BattleNotFoundError =
    apiError(
      Status.NotFound,
      "battle_not_found",
      "battle_not_found"
    )
  private val PlayerNotFoundError =
    apiError(
      Status.BadRequest,
      "player_not_found",
      "player_not_found"
    )
  private val BotCommandsNotSupportedError =
    apiError(
      Status.BadRequest,
      "bot_commands_not_supported",
      "bot_commands_not_supported"
    )

  def routes(battleStateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleCommandPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            decodeCommandRequest(request).flatMap {
              case Left(BattleCommandAPIRequestError.InvalidJsonObject) =>
                errorResponse(InvalidJsonObjectError)
              case Left(BattleCommandAPIRequestError.MissingTicket) =>
                errorResponse(CommandNotAuthorizedError)
              case Left(BattleCommandAPIRequestError.InvalidField(field)) =>
                errorResponse(invalidFieldError(field))
              case Right(command) =>
                blocking(battleStateService.acceptCommand(command)).flatMap {
                  case Right(accepted) =>
                    jsonOk(BattleCommandAcceptedResponse.fromAccepted(accepted).asJson)
                  case Left(error) =>
                    errorResponse(commandSubmitApiError(error))
                }
            }
          case _ =>
            errorResponse(MethodNotAllowedError)
        }
    }

  private def isBattleCommandPath(request: Request[IO]): Boolean =
    BattleCommandRequestTarget.isCommandPath(requestPath(request))

  private def decodeCommandRequest(request: Request[IO]): IO[Either[BattleCommandAPIRequestError, BattleCommandRequest]] =
    decodeJsonObjectBody(request, BattleCommandAPIRequestError.InvalidJsonObject)(BattleCommandAPIRequest.decodeCommand)

  private def commandSubmitApiError(error: BattleCommandSubmitError): HttpApiError =
    error match {
      case BattleCommandSubmitError.BattleNotFound            => BattleNotFoundError
      case BattleCommandSubmitError.PlayerNotFound            => PlayerNotFoundError
      case BattleCommandSubmitError.BotCommandsNotSupported   => BotCommandsNotSupportedError
      case BattleCommandSubmitError.CommandNotAuthorized      => CommandNotAuthorizedError
    }

  private def invalidFieldError(field: BattleCommandRequestField): HttpApiError = {
    val code = BattleCommandRequestField.errorCode(field)
    apiError(
      Status.BadRequest,
      code,
      code
    )
  }
}
