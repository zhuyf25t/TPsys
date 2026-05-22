package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import slaydemo.backend.http4s.Http4sRouteSupport.{blocking, corsNoContent, corsOk, errorResponse, jsonOk, requestPath, typedApiError}
import slaydemo.backend.shared.api.{HealthApiErrorCode, HealthRequestTarget}
import slaydemo.backend.shared.api.HealthJsonCodec.given
import slaydemo.backend.shared.services.HealthService

private[http4s] object HealthHttp4sRoutes {
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
