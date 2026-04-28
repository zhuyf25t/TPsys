package slaydemo.backend.bots.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.bots.objects.{BotProfileRecord, BotSkinProfile, DemoBotProfiles}

final class FileBotProfileRepository(storagePath: Path) extends BotProfileRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, BotProfileRecord]()

  loadFromDisk()

  override def list(): Seq[BotProfileRecord] = lock.synchronized {
    records.values().asScala.toSeq
      .sortBy(record => (record.profileOrder, record.botId))
  }

  override def save(record: BotProfileRecord): BotProfileRecord = lock.synchronized {
    records.put(record.botId, record)
    persist()
    record
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) {
      seedDefaults()
      persist()
      return
    }

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) {
      seedDefaults()
      persist()
      return
    }

    val parsed = extractProfileObjects(raw).zipWithIndex.flatMap { case (chunk, index) =>
      parseRecord(chunk, index)
    }

    if (parsed.nonEmpty) {
      parsed.foreach(record => records.put(record.botId, record))
    } else {
      seedDefaults()
      persist()
    }
  }

  private def seedDefaults(): Unit = {
    DemoBotProfiles.all.foreach { record =>
      records.put(record.botId, record)
    }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload(list())
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
        Console.err.println(s"[bots] failed to persist bot profiles at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(records: Seq[BotProfileRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.bot-profiles.v1",
       |  "profiles": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: BotProfileRecord): String = {
    s"""    {
       |      "botId": "${escape(record.botId)}",
       |      "handle": "${escape(record.handle)}",
       |      "displayName": "${escape(record.displayName)}",
       |      "initialRating": ${record.initialRating},
       |      "profileTone": "${escape(record.profileTone)}",
       |      "strategyLabel": "${escape(record.strategyLabel)}",
       |      "profileOrder": ${record.profileOrder},
       |      "skin": {
       |        "avatarKey": "${escape(record.skin.avatarKey)}",
       |        "textureKey": "${escape(record.skin.textureKey)}",
       |        "label": "${escape(record.skin.label)}"
       |      }
       |    }""".stripMargin
  }

  private def extractProfileObjects(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"profiles\"")
    if (marker < 0) return Seq.empty

    val start = raw.indexOf('[', marker)
    val end = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) return Seq.empty

    val section = raw.substring(start + 1, end)
    val chunks = scala.collection.mutable.ArrayBuffer.empty[String]
    val buffer = new StringBuilder
    var depth = 0
    var inString = false
    var escaped = false

    section.foreach { ch =>
      buffer.append(ch)
      if (inString) {
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '"') {
          inString = false
        }
      } else {
        ch match {
          case '"' =>
            inString = true
          case '{' =>
            depth += 1
          case '}' =>
            depth -= 1
            if (depth == 0) {
              chunks += buffer.result()
              buffer.clear()
            }
          case _ =>
        }
      }

      if (depth == 0 && !inString && buffer.nonEmpty && buffer.last != '}') {
        buffer.clear()
      }
    }

    chunks.toSeq
  }

  private def parseRecord(chunk: String, fallbackOrder: Int): Option[BotProfileRecord] = {
    for {
      botId <- extractString(chunk, "botId")
      handle <- extractString(chunk, "handle")
      displayName <- extractString(chunk, "displayName")
      initialRating <- extractInt(chunk, "initialRating")
      profileTone <- extractString(chunk, "profileTone")
      strategyLabel <- extractString(chunk, "strategyLabel")
      avatarKey <- extractString(chunk, "avatarKey")
      textureKey <- extractString(chunk, "textureKey")
      label <- extractString(chunk, "label")
    } yield BotProfileRecord(
      botId = botId,
      handle = handle,
      displayName = displayName,
      initialRating = initialRating,
      profileTone = profileTone,
      strategyLabel = strategyLabel,
      skin = BotSkinProfile(
        avatarKey = avatarKey,
        textureKey = textureKey,
        label = label
      ),
      profileOrder = extractInt(chunk, "profileOrder").getOrElse(fallbackOrder)
    )
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractInt(raw: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toInt)
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
