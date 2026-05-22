package slaydemo.backend.http4s.replay

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.replay.services.ReplayService

private[http4s] object ReplayHttpModule {
  def routes(service: ReplayService): HttpRoutes[IO] =
    ReplayHttp4sRoutes.catalogRoutes(service)
}
