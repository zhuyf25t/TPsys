package slaydemo.backend.http4s.identity

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.identity.services.IdentityService

private[http4s] object IdentityHttpModule {
  def routes(service: IdentityService): HttpRoutes[IO] =
    IdentityHttp4sRoutes.routes(service)
}
