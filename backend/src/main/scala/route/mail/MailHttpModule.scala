package route.mail

import cats.effect.IO
import org.http4s.HttpRoutes

import services.mail.services.MailService

private[route] object MailHttpModule {
  def routes(service: MailService): HttpRoutes[IO] =
    MailHttp4sRoutes.routes(service)
}
