package route.identity

import cats.effect.IO
import org.http4s.HttpRoutes

import services.identity.services.IdentityService

private[route] object IdentityHttpModule {
  def routes(service: IdentityService): HttpRoutes[IO] =
    IdentityHttp4sRoutes.routes(service)
}
