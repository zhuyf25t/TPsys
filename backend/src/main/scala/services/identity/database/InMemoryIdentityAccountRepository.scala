package services.identity.database

import java.util.concurrent.ConcurrentHashMap

import scala.jdk.CollectionConverters.*

import services.identity.objects.{
  AccountStatus,
  IdentityAccount,
  PasswordHash,
  PlainTextPassword,
  PlayerHandle,
  SessionToken
}

private final case class StoredIdentityAccount(
  account: IdentityAccount,
  passwordHash: PasswordHash
)

final class InMemoryIdentityAccountRepository extends IdentityAccountRepository {
  private val records = ConcurrentHashMap[String, StoredIdentityAccount]()

  override def findByHandle(handle: PlayerHandle): Option[IdentityAccount] =
    Option(records.get(handle.key)).map(_.account)

  override def findPasswordHashByHandle(handle: PlayerHandle): Option[PasswordHash] =
    Option(records.get(handle.key)).map(_.passwordHash)

  override def findBySessionToken(sessionToken: SessionToken): Option[IdentityAccount] =
    records
      .values()
      .asScala
      .collectFirst {
        case stored if stored.account.sessionToken.contains(sessionToken) => stored.account
      }

  override def authenticate(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
    Option(records.get(handle.key)).filter(_.passwordHash.value == passwordHash.value).map(_.account)

  override def authenticateLegacyPlaintext(handle: PlayerHandle, password: PlainTextPassword): Option[IdentityAccount] =
    None

  override def create(account: IdentityAccount, passwordHash: PasswordHash): IdentityAccountCreateResult =
    Option(records.putIfAbsent(account.handle.key, StoredIdentityAccount(account, passwordHash))) match {
      case None           => IdentityAccountCreateResult.Created(account)
      case Some(existing) => IdentityAccountCreateResult.HandleAlreadyExists(existing.account)
    }

  override def replacePasswordHash(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
    Option(records.get(handle.key)).map { current =>
      records.put(handle.key, current.copy(passwordHash = passwordHash))
      current.account
    }

  override def updateSession(handle: PlayerHandle, sessionToken: SessionToken): Option[IdentityAccount] =
    Option(records.get(handle.key)).map { current =>
      val updated = current.account.copy(sessionToken = Some(sessionToken), status = AccountStatus.Active)
      records.put(handle.key, current.copy(account = updated))
      updated
    }

  override def listActiveAccounts(): Vector[IdentityAccount] =
    records
      .values()
      .asScala
      .iterator
      .map(_.account)
      .filter(_.status == AccountStatus.Active)
      .toVector
      .sortBy(_.handle.key)
}
