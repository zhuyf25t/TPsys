package slaydemo.backend.identity.api

final case class IdentityRegisterRequest(handle: String, password: String, skinId: String)
final case class IdentitySessionRequest(handle: String, password: String)

final case class IdentityAuthResponse(handle: String, skinId: String, session: String)
