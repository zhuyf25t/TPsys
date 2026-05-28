package services.identity.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.identity.objects.apiTypes.{IdentityAuthResponse, IdentityRegistrationApiRequest}
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentityRegisterAPIMessage(
  request: IdentityRegistrationApiRequest
) extends APIMessageWithContext[IdentityService, IdentityAuthResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAuthResponse] =
    for
      command <- IO.fromEither(
        IdentityCommandParsers
          .parseRegistrationCommand(request)
          .left
          .map(error => IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromRegistrationParseError(error)))
      )
      account <- IO.blocking(service.register(command)).flatMap {
        case Right(value) =>
          IO.pure(value)
        case Left(error) =>
          IO.raiseError(IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromRegistrationServiceError(error)))
      }
    yield IdentityAuthResponse.fromAccount(account)
}

object IdentityRegisterAPIMessage {
  given Decoder[IdentityRegisterAPIMessage] =
    Decoder[IdentityRegistrationApiRequest].map(IdentityRegisterAPIMessage.apply)
}
