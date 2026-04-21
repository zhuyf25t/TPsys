package slaydemo.backend.identity.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.identity.objects.IdentityAccount
import slaydemo.backend.shared.objects.UserId

private final case class StoredIdentityAccount(account: IdentityAccount, password: String)

final class FileIdentityAccountRepository(storagePath: Path) extends IdentityAccountRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, StoredIdentityAccount]()

  loadFromDisk()

  override def findByHandle(handle: String): Option[IdentityAccount] = lock.synchronized {
    Option(records.get(normalize(handle))).map(_.account)
  }

  override def findBySessionToken(sessionToken: String): Option[IdentityAccount] = lock.synchronized {
    records.values().asScala.collectFirst {
      case stored if stored.account.sessionToken == sessionToken => stored.account
    }
  }

  override def listActiveAccounts(): Seq[IdentityAccount] = lock.synchronized {
    records.values().asScala.iterator.map(_.account).filter(_.active).toSeq.sortBy(_.handle.toLowerCase)
  }

  override def exists(handle: String): Boolean = lock.synchronized {
    records.containsKey(normalize(handle))
  }

  override def create(handle: String, password: String, skinId: String): IdentityAccount = lock.synchronized {
    val key = normalize(handle)
    val account = IdentityAccount(
      userId = UserId(UUID.randomUUID().toString),
      handle = handle,
      displayName = handle,
      skinId = skinId,
      sessionToken = "",
      active = true
    )

    records.put(key, StoredIdentityAccount(account, password))
    persist()
    account
  }

  override def authenticate(handle: String, password: String): Option[IdentityAccount] = lock.synchronized {
    Option(records.get(normalize(handle))).filter(_.password == password).map(_.account)
  }

  override def updateSession(handle: String, sessionToken: String): Option[IdentityAccount] = lock.synchronized {
    val key = normalize(handle)
    Option(records.get(key)).map { current =>
      val updated = current.account.copy(sessionToken = sessionToken, active = true)
      records.put(key, current.copy(account = updated))
      persist()
      updated
    }
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) {
      return
    }

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) {
      return
    }

    extractAccountsSection(raw)
      .flatMap(parseStoredAccount)
      .foreach { stored =>
        records.put(normalize(stored.account.handle), stored)
      }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload(records.values().asScala.toSeq.sortBy(_.account.handle.toLowerCase))
      Option(storagePath.getParent).foreach(path => Files.createDirectories(path))

      val tempPath = storagePath.resolveSibling(s"${storagePath.getFileName.toString}.tmp")
      Files.writeString(
        tempPath,
        payload,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
      )

      try {
        Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch {
        case _: java.nio.file.AtomicMoveNotSupportedException =>
          Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING)
      }
    } catch {
      case error: Throwable =>
        Console.err.println(s"[identity] failed to persist accounts at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(storedAccounts: Seq[StoredIdentityAccount]): String = {
    val renderedAccounts = storedAccounts
      .map(renderStoredAccount)
      .mkString(",\n")

    s"""{
       |  "schema": "slay-demo.identity-accounts.v1",
       |  "accounts": [
       |$renderedAccounts
       |  ]
       |}
       |""".stripMargin
  }

  private def renderStoredAccount(stored: StoredIdentityAccount): String = {
    val account = stored.account
    s"""    {
       |      "userId": "${escape(account.userId.value)}",
       |      "handle": "${escape(account.handle)}",
       |      "displayName": "${escape(account.displayName)}",
       |      "skinId": "${escape(account.skinId)}",
       |      "sessionToken": "${escape(account.sessionToken)}",
       |      "active": ${account.active},
       |      "password": "${escape(stored.password)}"
       |    }""".stripMargin
  }

  private def extractAccountsSection(raw: String): Seq[String] = {
    val accountsMarker = raw.indexOf("\"accounts\"")
    if (accountsMarker < 0) {
      return Seq.empty
    }

    val start = raw.indexOf('[', accountsMarker)
    val end = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) {
      return Seq.empty
    }

    val section = raw.substring(start + 1, end)
    "\\{([^{}]*)\\}".r.findAllMatchIn(section).map(_.group(1)).toSeq
  }

  private def parseStoredAccount(chunk: String): Option[StoredIdentityAccount] = {
    val handle = extractString(chunk, "handle")
    val password = extractString(chunk, "password")

    handle.flatMap { parsedHandle =>
      password.map { parsedPassword =>
        val account = IdentityAccount(
          userId = UserId(extractString(chunk, "userId").getOrElse(UUID.randomUUID().toString)),
          handle = parsedHandle,
          displayName = extractString(chunk, "displayName").getOrElse(parsedHandle),
          skinId = extractString(chunk, "skinId").getOrElse("blue"),
          sessionToken = extractString(chunk, "sessionToken").getOrElse(""),
          active = extractBoolean(chunk, "active").getOrElse(true)
        )

        StoredIdentityAccount(account, parsedPassword)
      }
    }
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractBoolean(raw: String, field: String): Option[Boolean] = {
    val pattern = s""""$field"\\s*:\\s*(true|false)""".r
    pattern.findFirstMatchIn(raw).map(matchResult => matchResult.group(1).toBoolean)
  }

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")

  private def normalize(handle: String): String = handle.trim.toLowerCase
}
