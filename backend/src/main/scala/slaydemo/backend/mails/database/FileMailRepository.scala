package slaydemo.backend.mails.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.mails.objects.MailRecord

final class FileMailRepository(storagePath: Path) extends MailRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, MailRecord]()

  loadFromDisk()

  override def listByOwner(ownerHandle: String): Seq[MailRecord] = lock.synchronized {
    records.values().asScala.toSeq
      .filter(record => normalize(record.ownerHandle) == normalize(ownerHandle))
      .sortBy(record => (-record.createdAt, record.id))
  }

  override def save(record: MailRecord): MailRecord = lock.synchronized {
    val key = recordKey(record.ownerHandle, record.id)
    if (!records.containsKey(key)) {
      records.put(key, record)
      persist()
    }
    record
  }

  override def markRead(ownerHandle: String, mailId: String): Boolean = lock.synchronized {
    val key = recordKey(ownerHandle, mailId)
    Option(records.get(key)).exists { record =>
      if (normalize(record.ownerHandle) != normalize(ownerHandle)) {
        false
      } else {
        if (record.unread) {
          records.put(key, record.copy(unread = false))
          persist()
        }
        true
      }
    }
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) return

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) return

    extractMailsSection(raw).flatMap(parseRecord).foreach { record =>
      records.put(recordKey(record.ownerHandle, record.id), record)
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
        Console.err.println(s"[mails] failed to persist mails at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(records: Seq[MailRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.mails.v1",
       |  "mails": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: MailRecord): String = {
    s"""    {
       |      "id": "${escape(record.id)}",
       |      "ownerHandle": "${escape(record.ownerHandle)}",
       |      "kind": "${escape(record.kind)}",
       |      "subject": "${escape(record.subject)}",
       |      "excerpt": "${escape(record.excerpt)}",
       |      "senderLabel": "${escape(record.senderLabel)}",
       |      "unread": ${record.unread},
       |      "important": ${record.important},
       |      "createdAt": ${record.createdAt}
       |    }""".stripMargin
  }

  private def extractMailsSection(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"mails\"")
    if (marker < 0) return Seq.empty

    val start = raw.indexOf('[', marker)
    val end = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) return Seq.empty

    val section = raw.substring(start + 1, end)
    "\\{([^{}]*)\\}".r.findAllMatchIn(section).map(_.group(1)).toSeq
  }

  private def parseRecord(chunk: String): Option[MailRecord] = {
    for {
      id <- extractString(chunk, "id")
      ownerHandle <- extractString(chunk, "ownerHandle")
      kind <- extractString(chunk, "kind")
      subject <- extractString(chunk, "subject")
      excerpt <- extractString(chunk, "excerpt")
      senderLabel <- extractString(chunk, "senderLabel")
      unread <- extractBoolean(chunk, "unread")
      important <- extractBoolean(chunk, "important")
      createdAt <- extractLong(chunk, "createdAt")
    } yield MailRecord(id, ownerHandle, kind, subject, excerpt, senderLabel, unread, important, createdAt)
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractBoolean(raw: String, field: String): Option[Boolean] = {
    val pattern = s""""$field"\\s*:\\s*(true|false)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toBoolean)
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

  private def normalize(handle: String): String = handle.trim.toLowerCase

  private def recordKey(ownerHandle: String, mailId: String): String =
    s"${normalize(ownerHandle)}\u0000${mailId.trim}"
}
