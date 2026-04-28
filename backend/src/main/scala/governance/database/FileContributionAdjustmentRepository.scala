package slaydemo.backend.governance.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.governance.objects.ContributionAdjustmentRecord

final class FileContributionAdjustmentRepository(storagePath: Path) extends ContributionAdjustmentRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, ContributionAdjustmentRecord]()

  loadFromDisk()

  override def list(limit: Int): Seq[ContributionAdjustmentRecord] = lock.synchronized {
    records.values().asScala.toSeq
      .sortBy(record => (-record.createdAt, record.id))
      .take(math.max(0, limit))
  }

  override def save(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord = lock.synchronized {
    records.put(record.id, record)
    persist()
    record
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) return

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) return

    extractAdjustmentsSection(raw).flatMap(parseRecord).foreach { record =>
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
          s"[governance] failed to persist contribution adjustments at ${storagePath.toAbsolutePath}: ${error.getMessage}"
        )
    }
  }

  private def renderPayload(records: Seq[ContributionAdjustmentRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.governance.contribution-adjustments.v1",
       |  "adjustments": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: ContributionAdjustmentRecord): String = {
    s"""    {
       |      "id": "${escape(record.id)}",
       |      "actorHandle": "${escape(record.actorHandle)}",
       |      "targetHandle": "${escape(record.targetHandle)}",
       |      "delta": ${record.delta},
       |      "reason": "${escape(record.reason)}",
       |      "createdAt": ${record.createdAt},
       |      "sourceLabel": "${escape(record.sourceLabel)}",
       |      "sourcePath": "${escape(record.sourcePath)}"
       |    }""".stripMargin
  }

  private def extractAdjustmentsSection(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"adjustments\"")
    if (marker < 0) return Seq.empty

    val start = raw.indexOf('[', marker)
    val end = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) return Seq.empty

    val section = raw.substring(start + 1, end)
    "\\{([^{}]*)\\}".r.findAllMatchIn(section).map(_.group(1)).toSeq
  }

  private def parseRecord(chunk: String): Option[ContributionAdjustmentRecord] = {
    for {
      id <- extractString(chunk, "id")
      actorHandle <- extractString(chunk, "actorHandle")
      targetHandle <- extractString(chunk, "targetHandle")
      delta <- extractInt(chunk, "delta")
      reason <- extractString(chunk, "reason")
      createdAt <- extractLong(chunk, "createdAt")
    } yield ContributionAdjustmentRecord(
      id,
      actorHandle,
      targetHandle,
      delta,
      reason,
      createdAt,
      extractString(chunk, "sourceLabel").getOrElse(""),
      extractString(chunk, "sourcePath").getOrElse("")
    )
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractInt(raw: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).flatMap(matchResult => matchResult.group(1).toIntOption)
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
