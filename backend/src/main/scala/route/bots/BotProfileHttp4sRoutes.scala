package route.bots

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import services.bots.api.{BotProfileApiErrorCode, BotProfileRequestTarget}
import services.bots.objects.apiTypes.BotProfilesResponse
import services.bots.services.BotProfileService
import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.{errorResponse, jsonOk}

private[route] object BotProfileHttp4sRoutes {
  def routes(service: BotProfileService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBotProfilePath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case Method.GET =>
            service.list().flatMap(records => jsonOk(BotProfilesResponse.fromRecords(records).asJson))
          case _ =>
            errorResponse(botProfileApiError(BotProfileApiErrorCode.MethodNotAllowed))
        }
    }

  private def isBotProfilePath(request: Request[IO]): Boolean =
    BotProfileRequestTarget.isProfilePath(requestPath(request))

  private def botProfileApiError(code: BotProfileApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = BotProfileApiErrorCode.statusCode(code),
      code = BotProfileApiErrorCode.wireValue(code),
      message = BotProfileApiErrorCode.message(code)
    )
}
