package services.mail.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.mail.objects.apiTypes.{MailListApiRequest, MailListResponse}
import services.mail.services.MailService
import system.api.APIMessageWithContext

final case class MailListAPIMessage(
  request: MailListApiRequest
) extends APIMessageWithContext[MailService, MailListResponse] {
  override def plan(service: MailService, connection: Connection): IO[MailListResponse] =
    for
      owner <- IO.fromEither(
        MailOwnerQuery.parse(request.ownerHandle).left.map(error =>
          MailAPIMessageSupport.error(MailApiErrorCode.fromOwnerError(error))
        )
      )
      records <- service.list(owner)
    yield MailListResponse.fromRecords(records)
}

object MailListAPIMessage {
  given Decoder[MailListAPIMessage] =
    Decoder[MailListApiRequest].map(MailListAPIMessage.apply)
}
