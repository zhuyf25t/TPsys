package services.identity.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.identity.objects.apiTypes.{IdentityAuthResponse, IdentitySessionApiRequest}
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentitySessionAPIMessage(
  request: IdentitySessionApiRequest
) extends APIMessageWithContext[IdentityService, IdentityAuthResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAuthResponse] =
    for
      command <- IO.fromEither(
        IdentityCommandParsers
          .parseSessionCommand(request)
          .left
          .map(error => IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromSessionParseError(error)))
      )
      account <- service.issueSession(command).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromSessionServiceError(error)))
      }
    yield IdentityAuthResponse.fromAccount(account)
}

object IdentitySessionAPIMessage {
  given Decoder[IdentitySessionAPIMessage] =
    Decoder[IdentitySessionApiRequest].map(IdentitySessionAPIMessage.apply)
}
