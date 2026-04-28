package slaydemo.backend.governance.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.governance.objects.GovernanceReviewNotificationRecord

final class FileGovernanceReviewNotificationRepository(storagePath: Path)
    extends GovernanceReviewNotificationRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, GovernanceReviewNotificationRecord]()

  loadFromDisk()

  override def list(
    kind: Option[String],
    targetType: Option[String],
    limit: Int
  ): Seq[GovernanceReviewNotificationRecord] = lock.synchronized {
    val normalizedKind = kind.map(_.trim).filter(_.nonEmpty)
    val normalizedTargetType = targetType.map(_.trim).filter(_.nonEmpty)

    records
      .values()
      .asScala
      .toSeq
      .filter(record => normalizedKind.forall(_ == record.kind))
      .filter(record => normalizedTargetType.forall(_ == record.targetType))
      .sortBy(record => (-record.createdAt, record.id))
      .take(math.max(0, limit))
  }

  override def save(record: GovernanceReviewNotificationRecord): GovernanceReviewNotificationRecord = lock.synchronized {
    records.put(record.id, record)
    persist()
    record
  }

  override def findByMailId(mailId: String): Option[GovernanceReviewNotificationRecord] = lock.synchronized {
    val normalizedMailId = mailId.trim
    if (normalizedMailId.isEmpty) {
      None
    } else {
      records.values().asScala.find(record => record.mailId == normalizedMailId)
    }
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) return

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) return

    extractNotificationsSection(raw).flatMap(parseRecord).foreach { record =>
      records.put(record.id, record)
    }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload(records.values().asScala.toSeq.sortBy(record => (-record.createdAt, record.id)))
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

      try Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      catch {
        case _: java.nio.file.AtomicMoveNotSupportedException =>
          Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING)
      }
    } catch {
      case error: Throwable =>
        Console.err.println(
          s"[governance] failed to persist review notifications at ${storagePath.toAbsolutePath}: ${error.getMessage}"
        )
    }
  }

  private def renderPayload(records: Seq[GovernanceReviewNotificationRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.governance.review-notifications.v1",
       |  "notifications": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: GovernanceReviewNotificationRecord): String = {
    s"""    {
       |      "id": "${escape(record.id)}",
       |      "actorHandle": "${escape(record.actorHandle)}",
       |      "kind": "${escape(record.kind)}",
       |      "targetType": "${escape(record.targetType)}",
       |      "targetId": "${escape(record.targetId)}",
       |      "targetTitle": "${escape(record.targetTitle)}",
       |      "targetPath": "${escape(record.targetPath)}",
       |      "body": "${escape(record.body)}",
       |      "createdAt": ${record.createdAt},
       |      "mailId": "${escape(record.mailId)}"
       |    }""".stripMargin
  }

  private def extractNotificationsSection(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"notifications\"")
    if (marker < 0) return Seq.empty

    val start = raw.indexOf('[', marker)
    if (start < 0) return Seq.empty

    val end = findMatchingBracket(raw, start)
    if (end < 0 || end <= start) return Seq.empty

    extractObjects(raw.substring(start + 1, end))
  }

  private def extractObjects(section: String): Seq[String] = {
    val chunks = Vector.newBuilder[String]
    var depth = 0
    var start = -1
    var inString = false
    var escaping = false
    var index = 0

    while (index < section.length) {
      val char = section.charAt(index)
      if (escaping) {
        escaping = false
      } else if (char == '\\' && inString) {
        escaping = true
      } else if (char == '"') {
        inString = !inString
      } else if (!inString && char == '{') {
        if (depth == 0) start = index
        depth += 1
      } else if (!inString && char == '}') {
        depth -= 1
        if (depth == 0 && start >= 0) {
          chunks += section.substring(start, index + 1)
          start = -1
        }
      }

      index += 1
    }

    chunks.result()
  }

  private def findMatchingBracket(raw: String, start: Int): Int = {
    var depth = 0
    var inString = false
    var escaping = false
    var index = start

    while (index < raw.length) {
      val char = raw.charAt(index)
      if (escaping) {
        escaping = false
      } else if (char == '\\' && inString) {
        escaping = true
      } else if (char == '"') {
        inString = !inString
      } else if (!inString && char == '[') {
        depth += 1
      } else if (!inString && char == ']') {
        depth -= 1
        if (depth == 0) return index
      }

      index += 1
    }

    -1
  }

  private def parseRecord(chunk: String): Option[GovernanceReviewNotificationRecord] = {
    for {
      id <- extractString(chunk, "id")
      actorHandle <- extractString(chunk, "actorHandle")
      kind <- extractString(chunk, "kind")
      targetType <- extractString(chunk, "targetType")
      targetId <- extractString(chunk, "targetId")
      targetTitle <- extractString(chunk, "targetTitle")
      targetPath <- extractString(chunk, "targetPath")
      body <- extractString(chunk, "body")
      createdAt <- extractLong(chunk, "createdAt")
      mailId <- extractString(chunk, "mailId")
    } yield GovernanceReviewNotificationRecord(
      id,
      actorHandle,
      kind,
      targetType,
      targetId,
      targetTitle,
      targetPath,
      body,
      createdAt,
      mailId
    )
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).flatMap(matchResult => matchResult.group(1).toLongOption)
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
}
