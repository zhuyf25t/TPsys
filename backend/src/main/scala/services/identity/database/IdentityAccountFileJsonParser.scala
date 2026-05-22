package services.identity.database

import services.identity.objects.{AccountStatus, DisplayName, IdentityAccount, PlayerHandle, SessionToken, SkinId}
import system.objects.UserId

private[database] object IdentityAccountFileJsonParser {
  def parseStoredAccounts(raw: String): Vector[FileStoredIdentityAccount] =
    extractAccountObjects(raw).flatMap(parseStoredAccount)

  private def extractAccountObjects(raw: String): Vector[String] = {
    val marker = raw.indexOf("\"accounts\"")
    if marker < 0 then Vector.empty
    else {
      val start = raw.indexOf('[', marker)
      val end = raw.lastIndexOf(']')
      if start < 0 || end < 0 || end <= start then Vector.empty
      else "\\{([^{}]*)\\}".r.findAllMatchIn(raw.substring(start + 1, end)).map(_.group(1)).toVector
    }
  }

  private def parseStoredAccount(chunk: String): Option[FileStoredIdentityAccount] =
    for {
      handle <- extractString(chunk, "handle")
      password <- extractString(chunk, "password")
    } yield {
      val account = IdentityAccount(
        userId = UserId(extractString(chunk, "userId").getOrElse(java.util.UUID.randomUUID().toString)),
        handle = PlayerHandle(handle),
        displayName = DisplayName(extractString(chunk, "displayName").getOrElse(handle)),
        skinId = extractString(chunk, "skinId").flatMap(SkinId.fromString).getOrElse(SkinId.Blue),
        sessionToken = extractString(chunk, "sessionToken").flatMap(SessionToken.fromString),
        status = AccountStatus.Active
      )

      FileStoredIdentityAccount(
        account = account,
        passwordSecret = password,
        active = extractBoolean(chunk, "active").getOrElse(true)
      )
    }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractBoolean(raw: String, field: String): Option[Boolean] = {
    val pattern = s""""$field"\\s*:\\s*(true|false)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toBoolean)
  }

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")
}
