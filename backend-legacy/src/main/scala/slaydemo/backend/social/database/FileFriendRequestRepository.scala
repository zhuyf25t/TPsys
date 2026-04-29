package slaydemo.backend.social.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.social.objects.FriendRequestRecord

final class FileFriendRequestRepository(storagePath: Path) extends FriendRequestRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, FriendRequestRecord]()

  loadFromDisk()

  override def findById(id: String): Option[FriendRequestRecord] = lock.synchronized {
    records.values().asScala.find(record => record.id == id.trim)
  }

  override def findByHandles(sourceHandle: String, targetHandle: String): Option[FriendRequestRecord] = lock.synchronized {
    Option(records.get(key(sourceHandle, targetHandle)))
  }

  override def listByOwner(ownerHandle: String): Seq[FriendRequestRecord] = lock.synchronized {
    val normalized = normalize(ownerHandle)
    records.values().asScala.toSeq
      .filter(record => normalize(record.sourceHandle) == normalized || normalize(record.targetHandle) == normalized)
      .sortBy(record => (-record.createdAt, record.id))
  }

  override def save(record: FriendRequestRecord): FriendRequestRecord = lock.synchronized {
    records.put(key(record.sourceHandle, record.targetHandle), record)
    persist()
    record
  }

  override def updateStatus(id: String, status: String, respondedAt: Long): Option[FriendRequestRecord] = lock.synchronized {
    records.values().asScala.find(record => record.id == id.trim).map { record =>
      val updated = record.copy(status = status, respondedAt = Some(respondedAt))
      records.put(key(updated.sourceHandle, updated.targetHandle), updated)
      persist()
      updated
    }
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) return

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) return

    extractRequestsSection(raw).flatMap(parseRecord).foreach { record =>
      records.put(key(record.sourceHandle, record.targetHandle), record)
    }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload(records.values().asScala.toSeq.sortBy(_.createdAt)(Ordering.Long.reverse))
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
        Console.err.println(s"[social] failed to persist friend requests at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(records: Seq[FriendRequestRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.friend-requests.v1",
       |  "requests": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: FriendRequestRecord): String = {
    s"""    {
       |      "id": "${escape(record.id)}",
       |      "sourceHandle": "${escape(record.sourceHandle)}",
       |      "targetHandle": "${escape(record.targetHandle)}",
       |      "createdAt": ${record.createdAt},
       |      "status": "${escape(record.status)}",
       |      "respondedAt": ${record.respondedAt.map(_.toString).getOrElse("null")}
       |    }""".stripMargin
  }

  private def extractRequestsSection(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"requests\"")
    if (marker < 0) return Seq.empty

    val start = raw.indexOf('[', marker)
    val end = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) return Seq.empty

    val section = raw.substring(start + 1, end)
    "\\{([^{}]*)\\}".r.findAllMatchIn(section).map(_.group(1)).toSeq
  }

  private def parseRecord(chunk: String): Option[FriendRequestRecord] = {
    for {
      id <- extractString(chunk, "id")
      sourceHandle <- extractString(chunk, "sourceHandle")
      targetHandle <- extractString(chunk, "targetHandle")
      createdAt <- extractLong(chunk, "createdAt")
    } yield FriendRequestRecord(
      id,
      sourceHandle,
      targetHandle,
      createdAt,
      extractString(chunk, "status").getOrElse("pending"),
      extractLong(chunk, "respondedAt")
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

  private def key(sourceHandle: String, targetHandle: String): String =
    s"${normalize(sourceHandle)}->${normalize(targetHandle)}"

  private def normalize(handle: String): String = handle.trim.toLowerCase
}
