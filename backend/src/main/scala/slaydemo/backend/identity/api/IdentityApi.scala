package slaydemo.backend.identity.api

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

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
}
