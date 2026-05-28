package route.mail

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.mail.api.{MailAPIMessageSupport, MailListAPIMessage, MailReadAPIMessage}
import services.mail.objects.apiTypes.{MailListResponse, MailReadResponse}
import services.mail.services.MailService
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object MailHttpModule {
  def routes(service: MailService): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          MailService,
          MailListAPIMessage,
          MailListResponse
        ](service, MailAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          MailService,
          MailReadAPIMessage,
          MailReadResponse
        ](service, MailAPIMessageSupport.invalidJsonObject)
      )
    ) <+> MailHttp4sRoutes.routes(service)
}
