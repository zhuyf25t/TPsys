package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.apiTypes.ReplayCatalogResponse
import slaydemo.backend.replay.services.ReplayService

private[http4s] object ReplayHttp4sRoutes {
  private val AllowedCatalogPaths: Set[String] =
    Set("/replay/catalog", "/api/replay/catalog", "/api/replaycatalogapi")
  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Method is not allowed.")

  def catalogRoutes(service: ReplayService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isReplayCatalogPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.HEAD =>
            IO.pure(withCors(Response[IO](Status.Ok)))
          case Method.GET =>
            blocking(service.list(limitFrom(request))).flatMap { records =>
              val response = ReplayCatalogResponse.fromRecords(
                records = records,
                selectedHandle = request.params.get("handle").flatMap(PlayerHandle.forLookup)
              )
              Ok(response.asJson).map(withCors)
            }
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def isReplayCatalogPath(request: Request[IO]): Boolean =
    AllowedCatalogPaths.contains(request.uri.path.renderString)

  private def limitFrom(request: Request[IO]): Int =
    request.params.get("limit").flatMap(_.toIntOption).getOrElse(25)
}
