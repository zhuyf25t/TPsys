package services.identity.api

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import services.identity.objects.{IdentityAccount, IdentityAccountSummary, SkinId}

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

final case class IdentityAccountsResponse(accounts: Vector[IdentityAccountSummary])

object IdentityAccountsResponse {
  private given Encoder[IdentityAccountSummary] =
    Encoder.forProduct3("handle", "displayName", "skinId")(summary =>
      (summary.handle.value, summary.displayName.value, SkinId.wireValue(summary.skinId))
    )

  given Encoder[IdentityAccountsResponse] = deriveEncoder
}
