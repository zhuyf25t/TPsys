package slaydemo.backend.governance.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

import slaydemo.backend.governance.objects.GovernanceReviewNotificationRecord

final class FileGovernanceReviewNotificationRepository(storagePath: Path)
    extends GovernanceReviewNotificationRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, GovernanceReviewNotificationRecord]()
  private final case class ObjectScanState(
    depth: Int,
    start: Int,
    inString: Boolean,
    escaping: Boolean,
    index: Int,
    chunks: Vector[String]
  )

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
    val initialState = ObjectScanState(
      depth = 0,
      start = -1,
      inString = false,
      escaping = false,
      index = 0,
      chunks = Vector.empty
    )
    scanObjects(section, initialState).chunks
  }

  @tailrec
  private def scanObjects(section: String, state: ObjectScanState): ObjectScanState =
    if (state.index >= section.length) {
      state
    } else {
      val char = section.charAt(state.index)
      val nextState =
        if (state.escaping) {
          state.copy(escaping = false)
        } else if (char == '\\' && state.inString) {
          state.copy(escaping = true)
        } else if (char == '"') {
          state.copy(inString = !state.inString)
        } else if (!state.inString && char == '{') {
          state.copy(
            depth = state.depth + 1,
            start = if (state.depth == 0) state.index else state.start
          )
        } else if (!state.inString && char == '}') {
          val nextDepth = state.depth - 1
          if (nextDepth == 0 && state.start >= 0) {
            state.copy(
              depth = nextDepth,
              start = -1,
              chunks = state.chunks :+ section.substring(state.start, state.index + 1)
            )
          } else {
            state.copy(depth = nextDepth)
          }
        } else {
          state
        }
      scanObjects(section, nextState.copy(index = state.index + 1))
    }

  private def findMatchingBracket(raw: String, start: Int): Int =
    scanBracket(raw, depth = 0, inString = false, escaping = false, index = start)

  @tailrec
  private def scanBracket(raw: String, depth: Int, inString: Boolean, escaping: Boolean, index: Int): Int =
    if (index >= raw.length) {
      -1
    } else {
      val char = raw.charAt(index)
      if (escaping) {
        scanBracket(raw, depth, inString, escaping = false, index + 1)
      } else if (char == '\\' && inString) {
        scanBracket(raw, depth, inString, escaping = true, index + 1)
      } else if (char == '"') {
        scanBracket(raw, depth, !inString, escaping = false, index + 1)
      } else if (!inString && char == '[') {
        scanBracket(raw, depth + 1, inString, escaping = false, index + 1)
      } else if (!inString && char == ']') {
        val nextDepth = depth - 1
        if (nextDepth == 0) index else scanBracket(raw, nextDepth, inString, escaping = false, index + 1)
      } else {
        scanBracket(raw, depth, inString, escaping = false, index + 1)
      }
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
