package services.identity.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlainTextPassword
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentitySessionAPIMessage(
  handle: Option[IdentityLookupHandleInput],
  password: Option[PlainTextPassword]
) extends APIMessageWithContext[IdentityService, IdentityAuthResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAuthResponse] =
    IdentityAPIPlanner.planSession(service, this)
}

object IdentitySessionAPIMessage {
  import IdentityAPIMessageDecoding.given

  given Decoder[IdentitySessionAPIMessage] =
    deriveDecoder[IdentitySessionAPIMessage]
}
