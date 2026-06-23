package services.identity.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.SessionToken
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentityCurrentAPIMessage(
  session: Option[SessionToken]
) extends APIMessageWithContext[IdentityService, IdentityAuthResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAuthResponse] =
    IdentityAPIPlanner.planCurrent(service, this)
}

object IdentityCurrentAPIMessage {
  import IdentityAPIMessageDecoding.given

  given Decoder[IdentityCurrentAPIMessage] =
    deriveDecoder[IdentityCurrentAPIMessage]
}
