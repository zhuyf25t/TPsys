package route.health

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import system.api.{APIMessageRouter, HealthAPIMessage}
import system.services.HealthService

private[route] object HealthHttpModule {
  def routes(service: HealthService): HttpRoutes[IO] =
    APIMessageRouter.routes(List(HealthAPIMessage.registered(service))) <+>
      HealthHttp4sRoutes.routes(service)
}
