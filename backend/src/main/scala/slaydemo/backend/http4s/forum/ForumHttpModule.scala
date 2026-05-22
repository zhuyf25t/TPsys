package slaydemo.backend.http4s.forum

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.forum.services.ForumService

private[http4s] object ForumHttpModule {
  def routes(service: ForumService): HttpRoutes[IO] =
    ForumHttp4sRoutes.routes(service)
}
