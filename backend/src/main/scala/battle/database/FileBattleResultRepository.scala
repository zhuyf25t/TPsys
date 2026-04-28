package slaydemo.backend.battle.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.battle.objects.BattleResultRecord
import slaydemo.backend.shared.objects.{BattleId, UserId}

final class FileBattleResultRepository(storagePath: Path) extends BattleResultRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, BattleResultRecord]()

  loadFromDisk()

  override def save(record: BattleResultRecord): BattleResultRecord = lock.synchronized {
    records.put(record.resultId, record)
    persist()
    record
  }

  override def list(limit: Int): Seq[BattleResultRecord] = lock.synchronized {
    records.values().asScala.toSeq.sortBy(_.finishedAt)(Ordering.Long.reverse).take(limit.max(0))
  }

  override def listByHandle(handle: String, limit: Int): Seq[BattleResultRecord] = lock.synchronized {
    list(Int.MaxValue)
      .filter(record => record.handle.value.equalsIgnoreCase(handle.trim))
      .take(limit.max(0))
  }

  override def listByBattleId(battleId: String, limit: Int): Seq[BattleResultRecord] = lock.synchronized {
    list(Int.MaxValue)
      .filter(record => normalize(record.battleId.value) == normalize(battleId))
      .take(limit.max(0))
  }

  override def listByHandleAndBattleId(handle: String, battleId: String, limit: Int): Seq[BattleResultRecord] = lock.synchronized {
    listByBattleId(battleId, Int.MaxValue)
      .filter(record => record.handle.value.equalsIgnoreCase(handle.trim))
      .take(limit.max(0))
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) {
      return
    }

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) {
      return
    }

    extractRecordsSection(raw)
      .flatMap(parseRecord)
      .foreach { record =>
        records.put(record.resultId, record)
      }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload(records.values().asScala.toSeq.sortBy(_.finishedAt)(Ordering.Long.reverse))
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
        Console.err.println(s"[battle] failed to persist results at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(storedResults: Seq[BattleResultRecord]): String = {
    val rendered = storedResults.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.battle-results.v1",
       |  "results": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: BattleResultRecord): String = {
    s"""    {
       |      "battleId": "${escape(record.battleId.value)}",
       |      "resultId": "${escape(record.resultId)}",
       |      "handle": "${escape(record.handle.value)}",
       |      "displayName": "${escape(record.displayName)}",
       |      "finishedAt": ${record.finishedAt},
       |      "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |      "durationMs": ${record.durationMs},
       |      "score": ${record.score},
       |      "placement": ${record.placement.map(_.toString).getOrElse("null")},
       |      "aliveAtEnd": ${record.aliveAtEnd},
       |      "ratingBefore": ${record.ratingBefore},
       |      "ratingDelta": ${record.ratingDelta},
       |      "ratingAfter": ${record.ratingAfter},
       |      "resultLabel": "${escape(record.resultLabel)}",
       |      "modeLabel": "${escape(record.modeLabel)}",
       |      "mapLabel": "${escape(record.mapLabel)}",
       |      "highlightLine": "${escape(record.highlightLine)}",
       |      "playersLine": "${escape(record.playersLine)}",
       |      "timelineHint": "${escape(record.timelineHint)}",
       |      "currentLoadout": ${record.currentLoadout.map(value => s""""${escape(value)}"""").getOrElse("null")}
       |    }""".stripMargin
  }

  private def extractRecordsSection(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"results\"")
    if (marker < 0) {
      return Seq.empty
    }

    val start = raw.indexOf('[', marker)
    val end = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) {
      return Seq.empty
    }

    val section = raw.substring(start + 1, end)
    "\\{([^{}]*)\\}".r.findAllMatchIn(section).map(_.group(1)).toSeq
  }

  private def parseRecord(chunk: String): Option[BattleResultRecord] = {
    val battleId = extractString(chunk, "battleId")
    val handle = extractString(chunk, "handle")
    val displayName = extractString(chunk, "displayName")
    val finishedAt = extractLong(chunk, "finishedAt")
    val finishedAtLabel = extractString(chunk, "finishedAtLabel")
    val durationMs = extractLong(chunk, "durationMs")
    val score = extractInt(chunk, "score")
    val placement = extractNullableInt(chunk, "placement")
    val aliveAtEnd = extractBoolean(chunk, "aliveAtEnd")
    val ratingBefore = extractInt(chunk, "ratingBefore")
    val ratingDelta = extractInt(chunk, "ratingDelta")
    val ratingAfter = extractInt(chunk, "ratingAfter")
    val resultLabel = extractString(chunk, "resultLabel")
    val modeLabel = extractString(chunk, "modeLabel")
    val mapLabel = extractString(chunk, "mapLabel")
    val highlightLine = extractString(chunk, "highlightLine")
    val playersLine = extractString(chunk, "playersLine")
    val timelineHint = extractString(chunk, "timelineHint")
    val currentLoadout = extractNullableString(chunk, "currentLoadout")

    for {
      parsedBattleId <- battleId
      parsedHandle <- handle
      parsedDisplayName <- displayName
      parsedFinishedAt <- finishedAt
      parsedFinishedAtLabel <- finishedAtLabel
      parsedDurationMs <- durationMs
      parsedScore <- score
      parsedAliveAtEnd <- aliveAtEnd
      parsedRatingBefore <- ratingBefore
      parsedRatingDelta <- ratingDelta
      parsedRatingAfter <- ratingAfter
      parsedResultLabel <- resultLabel
      parsedModeLabel <- modeLabel
      parsedMapLabel <- mapLabel
      parsedHighlightLine <- highlightLine
      parsedPlayersLine <- playersLine
      parsedTimelineHint <- timelineHint
    } yield BattleResultRecord(
      battleId = BattleId(parsedBattleId),
      handle = UserId(parsedHandle),
      displayName = parsedDisplayName,
      finishedAt = parsedFinishedAt,
      finishedAtLabel = parsedFinishedAtLabel,
      durationMs = parsedDurationMs,
      score = parsedScore,
      placement = placement,
      aliveAtEnd = parsedAliveAtEnd,
      ratingBefore = parsedRatingBefore,
      ratingDelta = parsedRatingDelta,
      ratingAfter = parsedRatingAfter,
      resultLabel = parsedResultLabel,
      modeLabel = parsedModeLabel,
      mapLabel = parsedMapLabel,
      highlightLine = parsedHighlightLine,
      playersLine = parsedPlayersLine,
      timelineHint = parsedTimelineHint,
      currentLoadout = currentLoadout
    )
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractNullableString(raw: String, field: String): Option[String] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if (nullPattern.findFirstIn(raw).nonEmpty) {
      None
    } else {
      extractString(raw, field)
    }
  }

  private def extractInt(raw: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toInt)
  }

  private def extractNullableInt(raw: String, field: String): Option[Int] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if (nullPattern.findFirstIn(raw).nonEmpty) {
      None
    } else {
      extractInt(raw, field)
    }
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toLong)
  }

  private def extractBoolean(raw: String, field: String): Option[Boolean] = {
    val pattern = s""""$field"\\s*:\\s*(true|false)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toBoolean)
  }

  private def escape(value: String): String =
    Option(value)
      .getOrElse("")
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

  private def normalize(battleId: String): String = battleId.trim.toLowerCase
}
