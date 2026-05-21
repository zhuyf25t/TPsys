package slaydemo.backend.identity.api

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*

import slaydemo.backend.identity.objects.{IdentityAccount, SkinId}

final case class IdentityAuthResponse(
  handle: String,
  skinId: String,
  session: String
)

object IdentityAuthResponse {
  given Encoder[IdentityAuthResponse] = deriveEncoder

  def fromAccount(account: IdentityAccount): IdentityAuthResponse =
    IdentityAuthResponse(
      handle = account.handle.value,
      skinId = SkinId.wireValue(account.skinId),
      session = account.sessionToken.map(_.value).getOrElse("")
    )

  def render(response: IdentityAuthResponse): String =
    response.asJson.noSpaces

  def renderAccount(account: IdentityAccount): String =
    render(fromAccount(account))
}

final case class IdentityAccountSummary(
  handle: String,
  displayName: String,
  skinId: String
)

object IdentityAccountSummary {
  given Encoder[IdentityAccountSummary] = deriveEncoder
}

final case class IdentityAccountsResponse(accounts: Vector[IdentityAccountSummary])

object IdentityAccountsResponse {
  given Encoder[IdentityAccountsResponse] = deriveEncoder

  def render(accounts: Vector[IdentityAccountSummary]): String =
    IdentityAccountsResponse(accounts).asJson.noSpaces
}

final case class IdentityErrorResponse(error: String, code: String)

object IdentityErrorResponse {
  given Encoder[IdentityErrorResponse] =
    Encoder.forProduct2("error", "code")(value => (value.error, value.code))

  def render(code: String, message: String): String =
    IdentityErrorResponse(error = message, code = code).asJson.noSpaces
}
