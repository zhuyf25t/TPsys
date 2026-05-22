package route.bots

import cats.effect.IO
import org.http4s.HttpRoutes

import services.bots.services.BotProfileService

private[route] object BotProfileHttpModule {
  def routes(service: BotProfileService): HttpRoutes[IO] =
    BotProfileHttp4sRoutes.routes(service)
}
