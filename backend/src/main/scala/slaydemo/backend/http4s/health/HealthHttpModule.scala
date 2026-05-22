package slaydemo.backend.http4s.health

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.shared.services.HealthService

private[http4s] object HealthHttpModule {
  def routes(service: HealthService): HttpRoutes[IO] =
    HealthHttp4sRoutes.routes(service)
}
