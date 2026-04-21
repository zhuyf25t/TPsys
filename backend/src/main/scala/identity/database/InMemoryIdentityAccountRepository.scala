package slaydemo.backend.identity.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.identity.objects.IdentityAccount
import slaydemo.backend.shared.objects.UserId

private final case class InMemoryStoredIdentityAccount(account: IdentityAccount, password: String)

final class InMemoryIdentityAccountRepository extends IdentityAccountRepository {
  private val records = new ConcurrentHashMap[String, InMemoryStoredIdentityAccount]()

  override def findByHandle(handle: String): Option[IdentityAccount] = {
    Option(records.get(normalize(handle))).map(_.account)
  }

  override def findBySessionToken(sessionToken: String): Option[IdentityAccount] = {
    records.values().asScala.collectFirst {
      case stored if stored.account.sessionToken == sessionToken => stored.account
    }
  }

  override def listActiveAccounts(): Seq[IdentityAccount] = {
    records.values().asScala.iterator.map(_.account).filter(_.active).toSeq.sortBy(_.handle.toLowerCase)
  }

  override def exists(handle: String): Boolean = records.containsKey(normalize(handle))

  override def create(handle: String, password: String, skinId: String): IdentityAccount = {
    val key = normalize(handle)
    val account = IdentityAccount(
      userId = UserId(UUID.randomUUID().toString),
      handle = handle,
      displayName = handle,
      skinId = skinId,
      sessionToken = "",
      active = true
    )

    records.put(key, InMemoryStoredIdentityAccount(account, password))
    account
  }

  override def authenticate(handle: String, password: String): Option[IdentityAccount] = {
    Option(records.get(normalize(handle))).filter(_.password == password).map(_.account)
  }

  override def updateSession(handle: String, sessionToken: String): Option[IdentityAccount] = {
    val key = normalize(handle)
    Option(records.get(key)).map { current =>
      val updated = current.account.copy(sessionToken = sessionToken, active = true)
      records.put(key, current.copy(account = updated))
      updated
    }
  }

  private def normalize(handle: String): String = handle.trim.toLowerCase
}
