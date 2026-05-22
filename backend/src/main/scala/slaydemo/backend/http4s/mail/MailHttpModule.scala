package slaydemo.backend.http4s.mail

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.mail.services.MailService

private[http4s] object MailHttpModule {
  def routes(service: MailService): HttpRoutes[IO] =
    MailHttp4sRoutes.routes(service)
}
