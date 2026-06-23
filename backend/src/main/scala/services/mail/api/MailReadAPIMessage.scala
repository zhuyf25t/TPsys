package services.mail.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.mail.objects.MailId
import services.mail.services.MailService
import system.api.APIMessageWithContext

final case class MailReadAPIMessage(
  ownerHandle: Option[PlayerHandle],
  mailId: Option[MailId]
) extends APIMessageWithContext[MailService, MailReadResponse] {
  override def plan(service: MailService, connection: Connection): IO[MailReadResponse] =
    MailAPIPlanner.planRead(service, this)
}

object MailReadAPIMessage {
  import MailAPIMessageDecoding.given

  given Decoder[MailReadAPIMessage] =
    deriveDecoder[MailReadAPIMessage]
}
