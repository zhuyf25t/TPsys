package services.identity.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.identity.objects.apiTypes.IdentityAccountsResponse
import services.identity.services.IdentityService
import system.api.APIMessageWithContext

final case class IdentityAccountsAPIMessage() extends APIMessageWithContext[IdentityService, IdentityAccountsResponse] {
  override def plan(service: IdentityService, connection: Connection): IO[IdentityAccountsResponse] =
    for
      accounts <- service.listActiveAccounts()
      response <- IO.pure(IdentityAccountsResponse(accounts))
    yield response
}

object IdentityAccountsAPIMessage {
  given Decoder[IdentityAccountsAPIMessage] =
    Decoder.const(IdentityAccountsAPIMessage())
}
