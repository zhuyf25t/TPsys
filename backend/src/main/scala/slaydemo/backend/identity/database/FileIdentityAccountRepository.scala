package slaydemo.backend.identity.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.identity.objects.{
  AccountStatus,
  IdentityAccount,
  PasswordHash,
  PlainTextPassword,
  PlayerHandle,
  SessionToken,
}
import slaydemo.backend.shared.database.AtomicFileWrite

private[database] final case class FileStoredIdentityAccount(
  account: IdentityAccount,
  passwordSecret: String,
  active: Boolean
)

final class FileIdentityAccountRepository(storagePath: Path) extends IdentityAccountRepository {
  private val lock = Object()
  private var recordsByHandle: Map[String, FileStoredIdentityAccount] = Map.empty

  loadFromDisk()

  override def findByHandle(handle: PlayerHandle): Option[IdentityAccount] =
    lock.synchronized {
      activeRecord(handle).map(_.account)
    }

  override def findBySessionToken(sessionToken: SessionToken): Option[IdentityAccount] =
    lock.synchronized {
      recordsByHandle.values.collectFirst {
        case stored if stored.active && stored.account.sessionToken.contains(sessionToken) => stored.account
      }
    }

  override def authenticate(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
    lock.synchronized {
      activeRecord(handle)
        .filter(stored => stored.passwordSecret == passwordHash.value)
        .map(_.account)
    }

  override def authenticateLegacyPlaintext(handle: PlayerHandle, password: PlainTextPassword): Option[IdentityAccount] =
    lock.synchronized {
      activeRecord(handle)
        .filter(stored => !looksLikeSha256Hash(stored.passwordSecret))
        .filter(stored => stored.passwordSecret == password.value)
        .map(_.account)
    }

  override def create(account: IdentityAccount, passwordHash: PasswordHash): IdentityAccountCreateResult =
    lock.synchronized {
      recordsByHandle.get(account.handle.key) match {
        case Some(existing) =>
          IdentityAccountCreateResult.HandleAlreadyExists(existing.account)
        case None =>
          val stored = FileStoredIdentityAccount(account, passwordHash.value, active = true)
          recordsByHandle = recordsByHandle.updated(account.handle.key, stored)
          persist()
          IdentityAccountCreateResult.Created(account)
      }
    }

  override def replacePasswordHash(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
    lock.synchronized {
      activeRecord(handle).map { current =>
        recordsByHandle = recordsByHandle.updated(handle.key, current.copy(passwordSecret = passwordHash.value))
        persist()
        current.account
      }
    }

  override def updateSession(handle: PlayerHandle, sessionToken: SessionToken): Option[IdentityAccount] =
    lock.synchronized {
      recordsByHandle.get(handle.key).map { current =>
        val updatedAccount = current.account.copy(sessionToken = Some(sessionToken), status = AccountStatus.Active)
        val updated = current.copy(account = updatedAccount, active = true)
        recordsByHandle = recordsByHandle.updated(handle.key, updated)
        persist()
        updatedAccount
      }
    }

  override def listActiveAccounts(): Vector[IdentityAccount] =
    lock.synchronized {
      recordsByHandle.values.toVector
    }.filter(_.active)
      .map(_.account)
      .sortBy(_.handle.key)

  private def activeRecord(handle: PlayerHandle): Option[FileStoredIdentityAccount] =
    recordsByHandle.get(handle.key).filter(_.active)

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(storagePath) then {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          recordsByHandle = IdentityAccountFileJsonParser
            .parseStoredAccounts(raw)
            .map(stored => stored.account.handle.key -> stored)
            .toMap
        }
      }
  }

  private def persist(): Unit = {
    val payload = IdentityAccountFileJsonRenderer.renderPayload(recordsByHandle.values.toVector.sortBy(_.account.handle.key))
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }

  private def looksLikeSha256Hash(value: String): Boolean =
    value.length == 64 && value.forall { char =>
      (char >= '0' && char <= '9') ||
        (char >= 'a' && char <= 'f') ||
        (char >= 'A' && char <= 'F')
    }
}

object FileIdentityAccountRepository {
  def apply(storagePath: Path): FileIdentityAccountRepository =
    new FileIdentityAccountRepository(storagePath)
}
