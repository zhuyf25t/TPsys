package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{blocking, withCors}
import slaydemo.backend.shared.api.{HealthErrorResponse, HealthRequestTarget}
import slaydemo.backend.shared.api.HealthJsonCodec.given
import slaydemo.backend.shared.services.HealthService

private[http4s] object HealthHttp4sRoutes {
  def routes(service: HealthService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isHealthPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.HEAD =>
            IO.pure(withCors(Response[IO](Status.Ok)))
          case Method.GET =>
            blocking(service.current).flatMap(response => Ok(response.asJson).map(withCors))
          case _ =>
            IO.pure(withCors(Response[IO](Status.MethodNotAllowed).withEntity(HealthErrorResponse.MethodNotAllowed.asJson)))
        }
    }

  private def isHealthPath(request: Request[IO]): Boolean =
    HealthRequestTarget.isHealthPath(request.uri.path.renderString)
}
