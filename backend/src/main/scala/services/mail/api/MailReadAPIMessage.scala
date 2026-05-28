package services.mail.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.mail.objects.apiTypes.{MailReadApiRequest, MailReadResponse}
import services.mail.services.{MailReadError, MailService}
import system.api.APIMessageWithContext

final case class MailReadAPIMessage(
  request: MailReadApiRequest
) extends APIMessageWithContext[MailService, MailReadResponse] {
  override def plan(service: MailService, connection: Connection): IO[MailReadResponse] =
    for
      command <- IO.fromEither(
        MailCommandParsers.parseReadCommand(request).left.map(error =>
          MailAPIMessageSupport.error(MailApiErrorCode.fromReadError(error))
        )
      )
      response <- IO.blocking(service.markRead(command.ownerHandle, command.mailId)).flatMap {
        case Right(_) =>
          IO.pure(MailReadResponse.Read)
        case Left(MailReadError.MailNotFound) =>
          IO.raiseError(MailAPIMessageSupport.error(MailApiErrorCode.MailNotFound))
      }
    yield response
}

object MailReadAPIMessage {
  given Decoder[MailReadAPIMessage] =
    Decoder[MailReadApiRequest].map(MailReadAPIMessage.apply)
}
