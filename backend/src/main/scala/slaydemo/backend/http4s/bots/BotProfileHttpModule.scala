package slaydemo.backend.http4s.bots

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.bots.services.BotProfileService

private[http4s] object BotProfileHttpModule {
  def routes(service: BotProfileService): HttpRoutes[IO] =
    BotProfileHttp4sRoutes.routes(service)
}
