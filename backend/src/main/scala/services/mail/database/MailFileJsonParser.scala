package services.mail.database

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle
import services.mail.objects.{
  FriendRequestMailMetadata,
  GovernanceMailActorHandle,
  GovernanceMailMetadata,
  GovernanceMailTargetLabel,
  GovernanceMailTargetPath,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailImportance,
  MailKind,
  MailReadState,
  MailRecord
}

private[database] object MailFileJsonParser {
  def parseRecords(raw: String): Vector[MailRecord] =
    extractMailObjects(raw).flatMap(parseRecord)

  private def extractMailObjects(raw: String): Vector[String] = {
    val marker = raw.indexOf("\"mails\"")
    if marker < 0 then Vector.empty
    else {
      val start = raw.indexOf('[', marker)
      val end = raw.lastIndexOf(']')
      if start < 0 || end < 0 || end <= start then Vector.empty
      else "\\{([^{}]*)\\}".r.findAllMatchIn(raw.substring(start + 1, end)).map(_.group(1)).toVector
    }
  }

  private def parseRecord(chunk: String): Option[MailRecord] =
    for {
      id <- extractString(chunk, "id")
      ownerHandle <- extractString(chunk, "ownerHandle")
      kindText <- extractString(chunk, "kind")
      kind <- MailKind.fromWire(kindText)
      subject <- extractString(chunk, "subject")
      excerpt <- extractString(chunk, "excerpt")
      senderLabel <- extractString(chunk, "senderLabel")
      unread <- extractBoolean(chunk, "unread")
      important <- extractBoolean(chunk, "important")
      createdAt <- extractLong(chunk, "createdAt")
    } yield MailRecord(
      id = MailId(id),
      ownerHandle = PlayerHandle(ownerHandle),
      kind = kind,
      subject = subject,
      excerpt = excerpt,
      senderLabel = senderLabel,
      readState = MailReadState.fromUnreadFlag(unread),
      importance = MailImportance.fromImportantFlag(important),
      createdAt = EpochMillis(createdAt),
      sourceBattleId = extractNullableString(chunk, "sourceBattleId"),
      sourcePath = extractNullableString(chunk, "sourcePath"),
      sourceLabel = extractNullableString(chunk, "sourceLabel"),
      governanceMetadata = parseGovernanceMetadata(chunk),
      friendRequestMetadata = parseFriendRequestMetadata(chunk)
    )

  private def parseGovernanceMetadata(chunk: String): Option[GovernanceMailMetadata] =
    for {
      actorHandle <- extractNullableString(chunk, "governanceActorHandle")
      targetPath <- extractNullableString(chunk, "governanceTargetPath")
      targetLabel <- extractNullableString(chunk, "governanceTargetLabel")
    } yield GovernanceMailMetadata(
      actorHandle = GovernanceMailActorHandle(actorHandle),
      targetPath = GovernanceMailTargetPath(targetPath),
      targetLabel = GovernanceMailTargetLabel(targetLabel)
    )

  private def parseFriendRequestMetadata(chunk: String): Option[FriendRequestMailMetadata] =
    for {
      requestId <- extractNullableString(chunk, "friendRequestId")
      statusText <- extractNullableString(chunk, "friendRequestStatus")
      status <- MailFriendRequestStatus.fromWire(statusText)
      sourceHandle <- extractNullableString(chunk, "friendRequestSourceHandle")
    } yield FriendRequestMailMetadata(
      requestId = MailFriendRequestId(requestId),
      status = status,
      sourceHandle = PlayerHandle(sourceHandle)
    )

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractNullableString(raw: String, field: String): Option[String] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if nullPattern.findFirstIn(raw).nonEmpty then None
    else extractString(raw, field).map(_.trim).filter(_.nonEmpty)
  }

  private def extractBoolean(raw: String, field: String): Option[Boolean] = {
    val pattern = s""""$field"\\s*:\\s*(true|false)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toBoolean)
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toLong)
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
