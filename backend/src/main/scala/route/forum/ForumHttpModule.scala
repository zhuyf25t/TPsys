package route.forum

import cats.effect.IO
import org.http4s.HttpRoutes

import services.forum.services.ForumService

private[route] object ForumHttpModule {
  def routes(service: ForumService): HttpRoutes[IO] =
    ForumHttp4sRoutes.routes(service)
}
