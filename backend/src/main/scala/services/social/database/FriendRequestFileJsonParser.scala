package services.social.database

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle
import services.social.objects.{FriendRequestId, FriendRequestRecord, FriendRequestStatus}

private[database] object FriendRequestFileJsonParser {
  def parseRecords(raw: String): Vector[FriendRequestRecord] =
    extractRequestObjects(raw).flatMap(parseRecord)

  private def extractRequestObjects(raw: String): Vector[String] = {
    val marker = raw.indexOf("\"requests\"")
    if marker < 0 then Vector.empty
    else {
      val start = raw.indexOf('[', marker)
      val end = raw.lastIndexOf(']')
      if start < 0 || end < 0 || end <= start then Vector.empty
      else "\\{([^{}]*)\\}".r.findAllMatchIn(raw.substring(start + 1, end)).map(_.group(1)).toVector
    }
  }

  private def parseRecord(chunk: String): Option[FriendRequestRecord] = {
    val parsedStatus = extractString(chunk, "status") match {
      case None        => Some(FriendRequestStatus.Pending)
      case Some(value) => FriendRequestStatus.fromWire(value)
    }

    for {
      id <- extractString(chunk, "id")
      sourceHandle <- extractString(chunk, "sourceHandle")
      targetHandle <- extractString(chunk, "targetHandle")
      createdAt <- extractLong(chunk, "createdAt")
      status <- parsedStatus
    } yield FriendRequestRecord(
      id = FriendRequestId(id),
      sourceHandle = PlayerHandle(sourceHandle),
      targetHandle = PlayerHandle(targetHandle),
      createdAt = EpochMillis(createdAt),
      status = status,
      respondedAt = extractNullableLong(chunk, "respondedAt").map(EpochMillis.apply)
    )
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toLong)
  }

  private def extractNullableLong(raw: String, field: String): Option[Long] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if nullPattern.findFirstIn(raw).nonEmpty then None else extractLong(raw, field)
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
