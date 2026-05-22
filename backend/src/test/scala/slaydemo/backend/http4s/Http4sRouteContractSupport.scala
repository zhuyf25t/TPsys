package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{HttpRoutes, Request}

private[http4s] object Http4sRouteContractSupport {
  final case class RouteResponse(status: Int, body: String)

  def runRoute(routes: HttpRoutes[IO], request: Request[IO]): RouteResponse = {
    val response = routes.orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }
}
