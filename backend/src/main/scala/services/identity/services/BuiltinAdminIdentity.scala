package services.identity.services

import services.identity.objects.{IdentityAccount, PasswordHash, PlayerHandle, SessionToken, SkinId}
import system.objects.UserId

private[services] object BuiltinAdminIdentity {
  val handle: PlayerHandle =
    PlayerHandle("admin")

  val passwordHash: PasswordHash =
    PasswordHash.unsafe("ac0e7d037817094e9e0b4441f9bae3209d67b02fa484917065f71b16109a1a78")

  private val userId: UserId =
    UserId("builtin-admin")

  def isHandle(candidate: PlayerHandle): Boolean =
    candidate.key == handle.key

  def account(sessionToken: Option[SessionToken]): IdentityAccount =
    IdentityAccount.active(
      userId = userId,
      handle = handle,
      skinId = SkinId.Blue,
      sessionToken = sessionToken
    )
}
