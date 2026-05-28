package services.identity.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.identity.objects.SessionToken
import services.identity.objects.apiTypes.{IdentityAuthResponse, IdentityCurrentApiRequest}
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentityCurrentAPIMessage(
  request: IdentityCurrentApiRequest
) extends APIMessageWithContext[IdentityService, IdentityAuthResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAuthResponse] =
    for
      account <- IO.blocking(service.current(sessionToken(request.session))).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromCurrentSessionError(error)))
      }
    yield IdentityAuthResponse.fromAccount(account)

  private def sessionToken(value: Option[String]): Option[SessionToken] =
    value.flatMap(raw => Option(raw).map(_.trim).filter(_.nonEmpty).map(SessionToken.apply))
}

object IdentityCurrentAPIMessage {
  given Decoder[IdentityCurrentAPIMessage] =
    Decoder[IdentityCurrentApiRequest].map(IdentityCurrentAPIMessage.apply)
}
