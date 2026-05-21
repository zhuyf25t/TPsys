package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.battle.api.BattleCommandRequest
import slaydemo.backend.battle.objects.apiTypes.{
  BattleCommandAcceptedResponse,
  BattleCommandAPIRequest,
  BattleCommandAPIRequestError
}
import slaydemo.backend.battle.services.{BattleCommandSubmitError, BattleStateService}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}

private[http4s] object BattleCommandHttp4sRoutes {
  private val AllowedPaths: Set[String] =
    Set(
      "/battle/command",
      "/battle/commands",
      "/api/battle/command",
      "/api/battle/commands",
      "/battlecommandapi",
      "/api/battlecommandapi"
    )

  private val InvalidJsonObjectError =
    HttpApiError(
      status = Status.BadRequest,
      code = "bad_request",
      message = "Request body must be a JSON object with supported primitive or object fields."
    )
  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Only POST and OPTIONS are supported.")
  private val CommandNotAuthorizedError =
    HttpApiError(status = Status.Forbidden, code = "command_not_authorized", message = "command_not_authorized")
  private val BattleNotFoundError =
    HttpApiError(status = Status.NotFound, code = "battle_not_found", message = "battle_not_found")
  private val PlayerNotFoundError =
    HttpApiError(status = Status.BadRequest, code = "player_not_found", message = "player_not_found")
  private val BotCommandsNotSupportedError =
    HttpApiError(status = Status.BadRequest, code = "bot_commands_not_supported", message = "bot_commands_not_supported")

  def routes(battleStateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleCommandPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            decodeCommandRequest(request).flatMap {
              case Left(BattleCommandAPIRequestError.InvalidJsonObject) =>
                IO.pure(apiError(InvalidJsonObjectError))
              case Left(BattleCommandAPIRequestError.MissingTicket) =>
                IO.pure(commandNotAuthorized)
              case Left(BattleCommandAPIRequestError.BadRequest(code)) =>
                IO.pure(apiError(badRequest(code)))
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
    AllowedPaths.contains(request.uri.path.renderString)

  private def decodeCommandRequest(request: Request[IO]): IO[Either[BattleCommandAPIRequestError, BattleCommandRequest]] =
    request.as[Json].attempt.map {
      case Left(_) =>
        Left(BattleCommandAPIRequestError.InvalidJsonObject)
      case Right(json) if json.asObject.isEmpty =>
        Left(BattleCommandAPIRequestError.InvalidJsonObject)
      case Right(json) =>
        BattleCommandAPIRequest.decodeCommand(json)
    }

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

  private def badRequest(code: String): HttpApiError =
    HttpApiError(status = Status.BadRequest, code = code, message = code)
}
