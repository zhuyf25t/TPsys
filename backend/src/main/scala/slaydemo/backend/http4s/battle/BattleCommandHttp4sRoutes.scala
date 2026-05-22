package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import slaydemo.backend.battle.objects.BattleCommandRequest
import slaydemo.backend.battle.objects.apiTypes.{
  BattleCommandAcceptedResponse,
  BattleCommandApiErrorCode,
  BattleCommandAPIRequest,
  BattleCommandAPIRequestError,
  BattleCommandRequestTarget
}
import slaydemo.backend.battle.services.{BattleCommandSubmitError, BattleStateService}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.HttpApiErrors.typedApiError
import slaydemo.backend.http4s.Http4sCors.corsNoContent
import slaydemo.backend.http4s.Http4sEffects.blocking
import slaydemo.backend.http4s.Http4sRequestDecoders.decodeJsonObjectBody
import slaydemo.backend.http4s.Http4sRequestPaths.requestPath
import slaydemo.backend.http4s.Http4sResponses.{errorResponse, jsonOk}

private[http4s] object BattleCommandHttp4sRoutes {
  def routes(battleStateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleCommandPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            decodeCommandRequest(request).flatMap {
              case Left(error) =>
                errorResponse(requestApiError(error))
              case Right(command) =>
                blocking(battleStateService.acceptCommand(command)).flatMap {
                  case Right(accepted) =>
                    jsonOk(BattleCommandAcceptedResponse.fromAccepted(accepted).asJson)
                  case Left(error) =>
                    errorResponse(commandSubmitApiError(error))
                }
            }
          case _ =>
            errorResponse(battleCommandApiError(BattleCommandApiErrorCode.MethodNotAllowed))
        }
    }

  private def isBattleCommandPath(request: Request[IO]): Boolean =
    BattleCommandRequestTarget.isCommandPath(requestPath(request))

  private def decodeCommandRequest(request: Request[IO]): IO[Either[BattleCommandAPIRequestError, BattleCommandRequest]] =
    decodeJsonObjectBody(request, BattleCommandAPIRequestError.InvalidJsonObject)(BattleCommandAPIRequest.decodeCommand)

  private def commandSubmitApiError(error: BattleCommandSubmitError): HttpApiError =
    battleCommandApiError(
      error match {
        case BattleCommandSubmitError.BattleNotFound =>
          BattleCommandApiErrorCode.BattleNotFound
        case BattleCommandSubmitError.PlayerNotFound =>
          BattleCommandApiErrorCode.PlayerNotFound
        case BattleCommandSubmitError.BotCommandsNotSupported =>
          BattleCommandApiErrorCode.BotCommandsNotSupported
        case BattleCommandSubmitError.CommandNotAuthorized =>
          BattleCommandApiErrorCode.CommandNotAuthorized
      }
    )

  private def requestApiError(error: BattleCommandAPIRequestError): HttpApiError =
    battleCommandApiError(BattleCommandApiErrorCode.fromRequestError(error))

  private def battleCommandApiError(code: BattleCommandApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = BattleCommandApiErrorCode.statusCode(code),
      code = BattleCommandApiErrorCode.wireValue(code),
      message = BattleCommandApiErrorCode.message(code)
    )
}
