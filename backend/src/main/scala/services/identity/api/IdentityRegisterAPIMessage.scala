package services.identity.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlainTextPassword
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentityRegisterAPIMessage(
  handle: Option[IdentityRegistrationHandleInput],
  password: Option[PlainTextPassword],
  skinId: Option[IdentitySkinIdInput]
) extends APIMessageWithContext[IdentityService, IdentityAuthResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAuthResponse] =
    IdentityAPIPlanner.planRegister(service, this)
}

object IdentityRegisterAPIMessage {
  import IdentityAPIMessageDecoding.given

  given Decoder[IdentityRegisterAPIMessage] =
    deriveDecoder[IdentityRegisterAPIMessage]
}
