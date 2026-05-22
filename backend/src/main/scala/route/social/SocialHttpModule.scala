package route.social

import cats.effect.IO
import org.http4s.HttpRoutes

import services.social.services.FriendRequestService

private[route] object SocialHttpModule {
  def routes(service: FriendRequestService): HttpRoutes[IO] =
    SocialHttp4sRoutes.routes(service)
}
