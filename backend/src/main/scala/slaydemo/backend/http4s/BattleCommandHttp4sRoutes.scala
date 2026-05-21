package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.battle.objects.BattleCommandRequest
import slaydemo.backend.battle.objects.apiTypes.{
  BattleCommandAcceptedResponse,
  BattleCommandAPIRequest,
  BattleCommandAPIRequestError,
  BattleCommandRequestField,
  BattleCommandRequestTarget
}
import slaydemo.backend.battle.services.{BattleCommandSubmitError, BattleStateService}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, codeMessageError, corsNoContent, decodeJsonObjectBody, methodNotAllowedError, requestPath, typedApiError, withCors}

private[http4s] object BattleCommandHttp4sRoutes {
  private val InvalidJsonObjectError =
    typedApiError(
      statusCode = 400,
      code = "bad_request",
      message = "Request body must be a JSON object with supported primitive or object fields."
    )
  private val MethodNotAllowedError =
    methodNotAllowedError("Only POST and OPTIONS are supported.")
  private val CommandNotAuthorizedError =
    codeMessageError(statusCode = 403, code = "command_not_authorized")
  private val BattleNotFoundError =
    codeMessageError(statusCode = 404, code = "battle_not_found")
  private val PlayerNotFoundError =
    codeMessageError(statusCode = 400, code = "player_not_found")
  private val BotCommandsNotSupportedError =
    codeMessageError(statusCode = 400, code = "bot_commands_not_supported")

  def routes(battleStateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleCommandPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            decodeCommandRequest(request).flatMap {
              case Left(BattleCommandAPIRequestError.InvalidJsonObject) =>
                IO.pure(apiError(InvalidJsonObjectError))
              case Left(BattleCommandAPIRequestError.MissingTicket) =>
                IO.pure(commandNotAuthorized)
              case Left(BattleCommandAPIRequestError.InvalidField(field)) =>
                IO.pure(apiError(invalidFieldError(field)))
              case Right(command) =>
                blocking(battleStateService.acceptCommand(command)).map {
                  case Right(accepted) =>
                    withCors(Response[IO](Status.Ok).withEntity(BattleCommandAcceptedResponse.fromAccepted(accepted)))
                  case Left(error) =>
                    commandSubmitError(error)
                }
            }
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def isBattleCommandPath(request: Request[IO]): Boolean =
    BattleCommandRequestTarget.isCommandPath(requestPath(request))

  private def decodeCommandRequest(request: Request[IO]): IO[Either[BattleCommandAPIRequestError, BattleCommandRequest]] =
    decodeJsonObjectBody(request, BattleCommandAPIRequestError.InvalidJsonObject)(BattleCommandAPIRequest.decodeCommand)

  private def commandSubmitError(error: BattleCommandSubmitError): Response[IO] =
    apiError(commandSubmitApiError(error))

  private def commandNotAuthorized: Response[IO] =
    apiError(CommandNotAuthorizedError)

  private def commandSubmitApiError(error: BattleCommandSubmitError): HttpApiError =
    error match {
      case BattleCommandSubmitError.BattleNotFound            => BattleNotFoundError
      case BattleCommandSubmitError.PlayerNotFound            => PlayerNotFoundError
      case BattleCommandSubmitError.BotCommandsNotSupported   => BotCommandsNotSupportedError
      case BattleCommandSubmitError.CommandNotAuthorized      => CommandNotAuthorizedError
    }

  private def invalidFieldError(field: BattleCommandRequestField): HttpApiError = {
    val code = BattleCommandRequestField.errorCode(field)
    codeMessageError(statusCode = 400, code = code)
  }
}
