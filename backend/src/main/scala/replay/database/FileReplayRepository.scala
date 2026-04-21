package slaydemo.backend.replay.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final class FileReplayRepository(storagePath: Path) extends ReplayRepository {
  private val lock = new Object
  private val records = new ConcurrentHashMap[String, ReplayRecord]()

  loadFromDisk()

  override def save(record: ReplayRecord): ReplayRecord = lock.synchronized {
    records.put(normalize(record.replayId.value), record)
    persist()
    record
  }

  override def list(limit: Int): Seq[ReplayRecord] = lock.synchronized {
    records.values().asScala.toSeq.sortBy(_.finishedAt)(Ordering.Long.reverse).take(limit.max(0))
  }

  override def findById(replayId: ReplayId): Option[ReplayRecord] = lock.synchronized {
    Option(records.get(normalize(replayId.value)))
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
        records.put(normalize(record.replayId.value), record)
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
        Console.err.println(s"[replay] failed to persist catalog at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(storedRecords: Seq[ReplayRecord]): String = {
    val rendered = storedRecords.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.replay-catalog.v1",
       |  "records": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: ReplayRecord): String = {
    val placement = record.placement.map(_.toString).getOrElse("null")
    val thumbnail = record.thumbnailDataUrl.map(jsonString).getOrElse("null")
    val currentLoadout = record.currentLoadout.map(jsonString).getOrElse("null")
    s"""    {
       |      "replayId": "${escape(record.replayId.value)}",
       |      "battleId": "${escape(record.battleId.value)}",
       |      "handle": "${escape(record.handle.value)}",
       |      "displayName": "${escape(record.displayName)}",
       |      "finishedAt": ${record.finishedAt},
       |      "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |      "title": "${escape(record.title)}",
       |      "modeLabel": "${escape(record.modeLabel)}",
       |      "resultLabel": "${escape(record.resultLabel)}",
       |      "mapLabel": "${escape(record.mapLabel)}",
       |      "highlightLine": "${escape(record.highlightLine)}",
       |      "coverLabel": "${escape(record.coverLabel)}",
       |      "playersLine": "${escape(record.playersLine)}",
       |      "timelineHint": "${escape(record.timelineHint)}",
       |      "score": ${record.score},
       |      "placement": $placement,
       |      "durationMs": ${record.durationMs},
       |      "aliveAtEnd": ${record.aliveAtEnd},
       |      "thumbnailDataUrl": $thumbnail,
       |      "currentLoadout": $currentLoadout,
       |      "frameCount": ${record.frameCount},
       |      "playbackAvailable": ${record.playbackAvailable},
       |      "framesJsonB64": "${escape(record.framesJsonB64)}"
       |    }""".stripMargin
  }

  private def extractRecordsSection(raw: String): Seq[String] = {
    val marker = raw.indexOf("\"records\"")
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

  private def parseRecord(chunk: String): Option[ReplayRecord] = {
    val replayId = extractString(chunk, "replayId")
    val battleId = extractString(chunk, "battleId")
    val handle = extractString(chunk, "handle")
    val displayName = extractString(chunk, "displayName")
    val finishedAt = extractLong(chunk, "finishedAt")
    val finishedAtLabel = extractString(chunk, "finishedAtLabel")
    val title = extractString(chunk, "title")
    val modeLabel = extractString(chunk, "modeLabel")
    val resultLabel = extractString(chunk, "resultLabel")
    val mapLabel = extractString(chunk, "mapLabel")
    val highlightLine = extractString(chunk, "highlightLine")
    val coverLabel = extractString(chunk, "coverLabel")
    val playersLine = extractString(chunk, "playersLine")
    val timelineHint = extractString(chunk, "timelineHint")
    val score = extractInt(chunk, "score")
    val placement = extractNullableInt(chunk, "placement")
    val durationMs = extractLong(chunk, "durationMs")
    val aliveAtEnd = extractBoolean(chunk, "aliveAtEnd")
    val thumbnailDataUrl = extractNullableString(chunk, "thumbnailDataUrl")
    val currentLoadout = extractNullableString(chunk, "currentLoadout")
    val frameCount = extractInt(chunk, "frameCount")
    val playbackAvailable = extractBoolean(chunk, "playbackAvailable")
    val framesJsonB64 = extractString(chunk, "framesJsonB64")

    for {
      parsedReplayId <- replayId
      parsedBattleId <- battleId
      parsedHandle <- handle
      parsedDisplayName <- displayName
      parsedFinishedAt <- finishedAt
      parsedFinishedAtLabel <- finishedAtLabel
      parsedTitle <- title
      parsedModeLabel <- modeLabel
      parsedResultLabel <- resultLabel
      parsedMapLabel <- mapLabel
      parsedHighlightLine <- highlightLine
      parsedCoverLabel <- coverLabel
      parsedPlayersLine <- playersLine
      parsedTimelineHint <- timelineHint
      parsedScore <- score
      parsedDurationMs <- durationMs
      parsedAliveAtEnd <- aliveAtEnd
      parsedFrameCount <- frameCount
      parsedPlaybackAvailable <- playbackAvailable
      parsedFramesJson <- framesJsonB64
    } yield ReplayRecord(
      replayId = ReplayId(parsedReplayId),
      battleId = BattleId(parsedBattleId),
      handle = UserId(parsedHandle),
      displayName = parsedDisplayName,
      finishedAt = parsedFinishedAt,
      finishedAtLabel = parsedFinishedAtLabel,
      title = parsedTitle,
      modeLabel = parsedModeLabel,
      resultLabel = parsedResultLabel,
      mapLabel = parsedMapLabel,
      highlightLine = parsedHighlightLine,
      coverLabel = parsedCoverLabel,
      playersLine = parsedPlayersLine,
      timelineHint = parsedTimelineHint,
      score = parsedScore,
      placement = placement,
      durationMs = parsedDurationMs,
      aliveAtEnd = parsedAliveAtEnd,
      thumbnailDataUrl = thumbnailDataUrl,
      currentLoadout = currentLoadout,
      frameCount = parsedFrameCount,
      playbackAvailable = parsedPlaybackAvailable,
      framesJsonB64 = parsedFramesJson
    )
  }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractNullableString(raw: String, field: String): Option[String] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if (nullPattern.findFirstIn(raw).nonEmpty) {
      Some(null)
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
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def jsonString(value: String): String = s"\"${escape(value)}\""

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")

  private def normalize(replayId: String): String = replayId.trim.toLowerCase
}
