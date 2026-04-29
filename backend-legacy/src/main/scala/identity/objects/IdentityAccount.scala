package slaydemo.backend.identity.objects

import slaydemo.backend.shared.objects.UserId

final case class IdentityAccount(
  userId: UserId,
  handle: String,
  displayName: String,
  skinId: String,
  sessionToken: String,
  active: Boolean
)
