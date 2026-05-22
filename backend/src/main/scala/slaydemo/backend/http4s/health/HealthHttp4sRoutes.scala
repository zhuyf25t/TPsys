package slaydemo.backend.http4s.health

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request, Status}

import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.HttpApiErrors.apiError
import slaydemo.backend.http4s.Http4sCors.{corsNoContent, corsOk}
import slaydemo.backend.http4s.Http4sEffects.blocking
import slaydemo.backend.http4s.Http4sRequestPaths.requestPath
import slaydemo.backend.http4s.Http4sResponses.{errorResponse, jsonOk}
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
    apiError(
      status = healthApiStatus(code),
      code = HealthApiErrorCode.wireValue(code),
      message = HealthApiErrorCode.message(code)
    )

  private def healthApiStatus(code: HealthApiErrorCode): Status =
    code match {
      case HealthApiErrorCode.MethodNotAllowed => Status.MethodNotAllowed
    }
}
