package slaydemo.backend.http4s.social

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.social.services.FriendRequestService

private[http4s] object SocialHttpModule {
  def routes(service: FriendRequestService): HttpRoutes[IO] =
    SocialHttp4sRoutes.routes(service)
}
