package services.mail.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.mail.services.MailService
import system.api.APIMessageWithContext

final case class MailListAPIMessage(
  ownerHandle: Option[PlayerHandle]
) extends APIMessageWithContext[MailService, MailListResponse] {
  override def plan(service: MailService, connection: Connection): IO[MailListResponse] =
    MailAPIPlanner.planList(service, this)
}

object MailListAPIMessage {
  import MailAPIMessageDecoding.given

  given Decoder[MailListAPIMessage] =
    deriveDecoder[MailListAPIMessage]
}
