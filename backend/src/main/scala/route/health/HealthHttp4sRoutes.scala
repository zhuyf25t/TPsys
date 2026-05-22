package route.health

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk}
import route.Http4sEffects.blocking
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.{errorResponse, jsonOk}
import system.objects.{HealthApiErrorCode, HealthRequestTarget}
import system.objects.HealthJsonCodec.given
import system.services.HealthService

private[route] object HealthHttp4sRoutes {
  def routes(service: HealthService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isHealthPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case Method.GET =>
            blocking(service.current).flatMap(response => jsonOk(response.asJson))
          case _ =>
            errorResponse(healthApiError(HealthApiErrorCode.MethodNotAllowed))
        }
    }

  private def isHealthPath(request: Request[IO]): Boolean =
    HealthRequestTarget.isHealthPath(requestPath(request))

  private def healthApiError(code: HealthApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = HealthApiErrorCode.statusCode(code),
      code = HealthApiErrorCode.wireValue(code),
      message = HealthApiErrorCode.message(code)
    )
}
