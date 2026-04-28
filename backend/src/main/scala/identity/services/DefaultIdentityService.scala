package slaydemo.backend.identity.services

import java.util.UUID

import scala.collection.concurrent.TrieMap

import slaydemo.backend.identity.database.IdentityAccountRepository
import slaydemo.backend.identity.objects.IdentityAccount
import slaydemo.backend.shared.objects.UserId

final class DefaultIdentityService(repository: IdentityAccountRepository) extends IdentityService {
  private val allowedSkins = Set("blue", "survivor", "soldier", "old")
  private val builtinAdminHandle = "admin"
  private val builtinAdminPassword = "admin123456"
  private val builtinAdminUserId = UserId("builtin-admin")
  private val builtinSessions = TrieMap.empty[String, IdentityAccount]

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
    } else if (isBuiltinAdminHandle(normalizedHandle)) {
      Left("handle_taken")
    } else if (repository.exists(normalizedHandle)) {
      Left("handle_taken")
    } else {
      val created = repository.create(normalizedHandle, normalizedPassword, normalizedSkinId)
      val sessionToken = newSessionToken(normalizedHandle)
      Right(repository.updateSession(normalizedHandle, sessionToken).getOrElse(created.copy(sessionToken = sessionToken)))
    }
  }

  override def issueSession(handle: String, password: String): Either[String, IdentityAccount] = {
    if (isBuiltinAdminHandle(handle) && password.trim == builtinAdminPassword) {
      val sessionToken = newSessionToken(builtinAdminHandle)
      val account = builtinAdminAccount(sessionToken)
      builtinSessions.put(sessionToken, account)
      return Right(account)
    }

    repository.authenticate(handle.trim, password.trim) match {
      case None => Left("invalid_credentials")
      case Some(account) =>
        val sessionToken = newSessionToken(account.handle)
        Right(repository.updateSession(account.handle, sessionToken).getOrElse(account.copy(sessionToken = sessionToken)))
    }
  }

  override def loadAccount(handle: String): Option[IdentityAccount] =
    if (isBuiltinAdminHandle(handle)) Some(builtinAdminAccount()) else repository.findByHandle(handle)

  override def loadAccountBySessionToken(sessionToken: String): Option[IdentityAccount] =
    builtinSessions.get(sessionToken).orElse(repository.findBySessionToken(sessionToken))

  override def listActiveAccounts(): Seq[IdentityAccount] =
    (repository.listActiveAccounts().filterNot(account => isBuiltinAdminHandle(account.handle)) :+ builtinAdminAccount())
      .sortBy(_.handle.toLowerCase)

  private def newSessionToken(handle: String): String = {
    val suffix = UUID.randomUUID().toString.replace("-", "").take(12)
    s"session-${handle.toLowerCase}-$suffix"
  }

  private def builtinAdminAccount(sessionToken: String = ""): IdentityAccount =
    IdentityAccount(
      userId = builtinAdminUserId,
      handle = builtinAdminHandle,
      displayName = builtinAdminHandle,
      skinId = "blue",
      sessionToken = sessionToken,
      active = true
    )

  private def isBuiltinAdminHandle(handle: String): Boolean = handle.trim.equalsIgnoreCase(builtinAdminHandle)
}
