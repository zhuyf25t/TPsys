package services.mail.api

import io.circe.Decoder

import services.identity.objects.PlayerHandle
import services.mail.objects.MailId
import system.policies.HandlePolicy

private[api] object MailAPIMessageDecoding {
  given optionalPlayerHandleDecoder: Decoder[Option[PlayerHandle]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(playerHandleFromWire))

  given optionalMailIdDecoder: Decoder[Option[MailId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(MailId.apply))

  def playerHandleFromWire(value: String): Option[PlayerHandle] =
    nonEmpty(value).map(PlayerHandle.apply)

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(HandlePolicy.trim).filter(_.nonEmpty)
}
