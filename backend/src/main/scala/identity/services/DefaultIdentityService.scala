package slaydemo.backend.identity.services

import java.util.UUID

import slaydemo.backend.identity.database.IdentityAccountRepository
import slaydemo.backend.identity.objects.IdentityAccount

final class DefaultIdentityService(repository: IdentityAccountRepository) extends IdentityService {
  private val allowedSkins = Set("blue", "survivor", "soldier", "old")

  override def register(handle: String, password: String, skinId: String): Either[String, IdentityAccount] = {
    val normalizedHandle = handle.trim
    val normalizedPassword = password.trim
    val normalizedSkinId = skinId.trim.toLowerCase

    if (normalizedHandle.length < 3 || normalizedHandle.length > 16) {
      Left("invalid_handle")
    } else if (!normalizedHandle.matches("^[a-zA-Z0-9_-]+$")) {
      Left("invalid_handle")
    } else if (normalizedPassword.length < 4) {
      Left("invalid_password")
    } else if (!allowedSkins.contains(normalizedSkinId)) {
      Left("invalid_skin")
    } else if (repository.exists(normalizedHandle)) {
      Left("handle_taken")
    } else {
      val created = repository.create(normalizedHandle, normalizedPassword, normalizedSkinId)
      val sessionToken = newSessionToken(normalizedHandle)
      Right(repository.updateSession(normalizedHandle, sessionToken).getOrElse(created.copy(sessionToken = sessionToken)))
    }
  }

  override def issueSession(handle: String, password: String): Either[String, IdentityAccount] = {
    repository.authenticate(handle.trim, password.trim) match {
      case None => Left("invalid_credentials")
      case Some(account) =>
        val sessionToken = newSessionToken(account.handle)
        Right(repository.updateSession(account.handle, sessionToken).getOrElse(account.copy(sessionToken = sessionToken)))
    }
  }

  override def loadAccount(handle: String): Option[IdentityAccount] = repository.findByHandle(handle)

  override def loadAccountBySessionToken(sessionToken: String): Option[IdentityAccount] =
    repository.findBySessionToken(sessionToken)

  override def listActiveAccounts(): Seq[IdentityAccount] = repository.listActiveAccounts()

  private def newSessionToken(handle: String): String = {
    val suffix = UUID.randomUUID().toString.replace("-", "").take(12)
    s"session-${handle.toLowerCase}-$suffix"
  }
}
