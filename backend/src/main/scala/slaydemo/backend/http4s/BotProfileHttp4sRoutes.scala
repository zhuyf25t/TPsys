package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request, Status}

import slaydemo.backend.bots.objects.apiTypes.{BotProfileApiErrorCode, BotProfileRequestTarget, BotProfilesResponse}
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, corsOk, errorResponse, jsonOk, requestPath}

private[http4s] object BotProfileHttp4sRoutes {
  def routes(service: BotProfileService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBotProfilePath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case Method.GET =>
            blocking(service.list()).flatMap(records => jsonOk(BotProfilesResponse.fromRecords(records).asJson))
          case _ =>
            errorResponse(botProfileApiError(BotProfileApiErrorCode.MethodNotAllowed))
        }
    }

  private def isBotProfilePath(request: Request[IO]): Boolean =
    BotProfileRequestTarget.isProfilePath(requestPath(request))

  private def botProfileApiError(code: BotProfileApiErrorCode): HttpApiError =
    apiError(
      status = botProfileApiStatus(code),
      code = BotProfileApiErrorCode.wireValue(code),
      message = BotProfileApiErrorCode.message(code)
    )

  private def botProfileApiStatus(code: BotProfileApiErrorCode): Status =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => Status.MethodNotAllowed
    }
}
