package slaydemo.backend.identity.api

final case class IdentityAuthResponse(
  handle: String,
  skinId: String,
  session: String
)

final case class IdentityAccountSummary(
  handle: String,
  displayName: String,
  skinId: String
)
