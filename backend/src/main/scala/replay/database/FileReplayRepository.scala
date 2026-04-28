package slaydemo.backend.replay.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayRecord}
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final class FileReplayRepository(storagePath: Path) extends ReplayRepository {
  private sealed trait FlatJsonValue
  private object FlatJsonValue {
    final case class JsonString(value: String) extends FlatJsonValue
    final case class JsonNumber(raw: String) extends FlatJsonValue
    final case class JsonBoolean(value: Boolean) extends FlatJsonValue
    case object JsonNull extends FlatJsonValue
  }

  private val lock = new Object
  private val records = new ConcurrentHashMap[String, ReplayRecord]()
  private val comments = new ConcurrentHashMap[String, ReplayCommentRecord]()

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

  override def delete(replayId: ReplayId): Unit = lock.synchronized {
    val normalizedReplayId = normalize(replayId.value)
    val removedRecord = records.remove(normalizedReplayId)
    val commentKeysToRemove = comments.values().asScala.collect {
      case comment if normalize(comment.replayId.value) == normalizedReplayId => normalize(comment.id)
    }

    commentKeysToRemove.foreach(key => comments.remove(key))

    if (removedRecord != null || commentKeysToRemove.nonEmpty) {
      persist()
    }
  }

  override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord = lock.synchronized {
    comments.put(normalize(record.id), record)
    persist()
    record
  }

  override def listComments(replayId: ReplayId, limit: Int): Seq[ReplayCommentRecord] = lock.synchronized {
    val normalizedReplayId = normalize(replayId.value)
    comments.values().asScala.toSeq
      .filter(comment => normalize(comment.replayId.value) == normalizedReplayId)
      .sortBy(comment => (comment.createdAt, comment.id))
      .take(limit.max(0))
  }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) {
      return
    }

    try {
      val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
      if (raw.nonEmpty) {
        extractRecordsSection(raw).foreach { chunk =>
          safeParseRecord(chunk).foreach { record =>
            records.put(normalize(record.replayId.value), record)
          }
        }

        extractCommentsSection(raw).foreach { chunk =>
          safeParseComment(chunk).foreach { record =>
            comments.put(normalize(record.id), record)
          }
        }
      }
    } catch {
      case error: Throwable =>
        Console.err.println(s"[replay] failed to load catalog at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload(
        records.values().asScala.toSeq.sortBy(_.finishedAt)(Ordering.Long.reverse),
        comments.values().asScala.toSeq.sortBy(comment => (comment.createdAt, comment.id))
      )
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

  private def renderPayload(storedRecords: Seq[ReplayRecord], storedComments: Seq[ReplayCommentRecord]): String = {
    val rendered = storedRecords.map(renderRecord).mkString(",\n")
    val renderedComments = storedComments.map(renderComment).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.replay-catalog.v1",
       |  "records": [
       |$rendered
       |  ],
       |  "comments": [
       |$renderedComments
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

  private def renderComment(record: ReplayCommentRecord): String = {
    s"""    {
       |      "id": "${escape(record.id)}",
       |      "replayId": "${escape(record.replayId.value)}",
       |      "authorHandle": "${escape(record.authorHandle.value)}",
       |      "body": "${escape(record.body)}",
       |      "createdAt": ${record.createdAt}
       |    }""".stripMargin
  }

  private def extractRecordsSection(raw: String): Seq[String] = {
    extractArraySection(raw, "records")
  }

  private def extractCommentsSection(raw: String): Seq[String] = {
    extractArraySection(raw, "comments")
  }

  private def extractArraySection(raw: String, field: String): Seq[String] = {
    val marker = raw.indexOf(s""""$field"""")
    if (marker < 0) {
      return Seq.empty
    }

    val start = raw.indexOf('[', marker)
    val end = findMatchingBracket(raw, start)
    if (start < 0 || end < 0 || end <= start) {
      return Seq.empty
    }

    val section = raw.substring(start + 1, end)
    extractObjectChunks(section)
  }

  private def extractObjectChunks(section: String): Seq[String] = {
    val chunks = Vector.newBuilder[String]
    var index = 0

    while (index < section.length) {
      index = skipWhitespace(section, index)

      while (index < section.length && section.charAt(index) == ',') {
        index = skipWhitespace(section, index + 1)
      }

      if (index >= section.length) {
        return chunks.result()
      }

      if (section.charAt(index) == '{') {
        val end = findMatchingDelimiter(section, index, '{', '}')
        if (end < 0) {
          Console.err.println(s"[replay] truncated catalog object while reading ${storagePath.toAbsolutePath}")
          return chunks.result()
        }

        chunks += section.substring(index, end + 1)
        index = end + 1
      } else {
        index = findNextArraySeparator(section, index)
      }
    }

    chunks.result()
  }

  private def findMatchingBracket(raw: String, start: Int): Int = {
    findMatchingDelimiter(raw, start, '[', ']')
  }

  private def findMatchingDelimiter(raw: String, start: Int, open: Char, close: Char): Int = {
    if (start < 0 || start >= raw.length || raw.charAt(start) != open) {
      return -1
    }

    var depth = 0
    var inString = false
    var escaped = false
    var index = start

    while (index < raw.length) {
      val ch = raw.charAt(index)
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
          case value if value == open =>
            depth += 1
          case value if value == close =>
            depth -= 1
            if (depth == 0) {
              return index
            }
          case _ =>
        }
      }

      index += 1
    }

    -1
  }

  private def parseRecord(fields: Map[String, FlatJsonValue]): Option[ReplayRecord] = {
    val replayId = extractString(fields, "replayId")
    val battleId = extractString(fields, "battleId")
    val handle = extractString(fields, "handle")
    val displayName = extractString(fields, "displayName")
    val finishedAt = extractLong(fields, "finishedAt")
    val finishedAtLabel = extractString(fields, "finishedAtLabel")
    val title = extractString(fields, "title")
    val modeLabel = extractString(fields, "modeLabel")
    val resultLabel = extractString(fields, "resultLabel")
    val mapLabel = extractString(fields, "mapLabel")
    val highlightLine = extractString(fields, "highlightLine")
    val coverLabel = extractString(fields, "coverLabel")
    val playersLine = extractString(fields, "playersLine")
    val timelineHint = extractString(fields, "timelineHint")
    val score = extractInt(fields, "score")
    val placement = extractNullableInt(fields, "placement")
    val durationMs = extractLong(fields, "durationMs")
    val aliveAtEnd = extractBoolean(fields, "aliveAtEnd")
    val thumbnailDataUrl = extractNullableString(fields, "thumbnailDataUrl")
    val currentLoadout = extractNullableString(fields, "currentLoadout")
    val frameCount = extractInt(fields, "frameCount")
    val playbackAvailable = extractBoolean(fields, "playbackAvailable")
    val framesJsonB64 = extractString(fields, "framesJsonB64")

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
      parsedThumbnailDataUrl <- thumbnailDataUrl
      parsedCurrentLoadout <- currentLoadout
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
      thumbnailDataUrl = parsedThumbnailDataUrl,
      currentLoadout = parsedCurrentLoadout,
      frameCount = parsedFrameCount,
      playbackAvailable = parsedPlaybackAvailable,
      framesJsonB64 = parsedFramesJson
    )
  }

  private def safeParseRecord(chunk: String): Option[ReplayRecord] = {
    try {
      parseFlatObject(chunk) match {
        case Left(error) =>
          val replayId = previewStringField(chunk, "replayId").getOrElse("<unknown>")
          Console.err.println(s"[replay] skipping corrupted replay $replayId from file catalog: $error")
          None
        case Right(fields) =>
          parseRecord(fields).orElse {
            val replayId = extractString(fields, "replayId").getOrElse("<unknown>")
            Console.err.println(s"[replay] skipping corrupted replay $replayId from file catalog: missing or invalid field")
            None
          }
      }
    } catch {
      case error: Throwable =>
        val replayId = previewStringField(chunk, "replayId").getOrElse("<unknown>")
        Console.err.println(s"[replay] skipping corrupted replay $replayId from file catalog: ${error.getMessage}")
        None
    }
  }

  private def parseComment(fields: Map[String, FlatJsonValue]): Option[ReplayCommentRecord] = {
    val id = extractString(fields, "id")
    val replayId = extractString(fields, "replayId")
    val authorHandle = extractString(fields, "authorHandle")
    val body = extractString(fields, "body")
    val createdAt = extractLong(fields, "createdAt")

    for {
      parsedId <- id
      parsedReplayId <- replayId
      parsedAuthorHandle <- authorHandle
      parsedBody <- body
      parsedCreatedAt <- createdAt
    } yield ReplayCommentRecord(
      id = parsedId,
      replayId = ReplayId(parsedReplayId),
      authorHandle = UserId(parsedAuthorHandle),
      body = parsedBody,
      createdAt = parsedCreatedAt
    )
  }

  private def safeParseComment(chunk: String): Option[ReplayCommentRecord] = {
    try {
      parseFlatObject(chunk) match {
        case Left(error) =>
          val commentId = previewStringField(chunk, "id").getOrElse("<unknown>")
          Console.err.println(s"[replay] skipping corrupted replay comment $commentId from file catalog: $error")
          None
        case Right(fields) =>
          parseComment(fields).orElse {
            val commentId = extractString(fields, "id").getOrElse("<unknown>")
            Console.err.println(s"[replay] skipping corrupted replay comment $commentId from file catalog: missing or invalid field")
            None
          }
      }
    } catch {
      case error: Throwable =>
        val commentId = previewStringField(chunk, "id").getOrElse("<unknown>")
        Console.err.println(s"[replay] skipping corrupted replay comment $commentId from file catalog: ${error.getMessage}")
        None
    }
  }

  private def parseFlatObject(raw: String): Either[String, Map[String, FlatJsonValue]] = {
    val source = raw.trim
    if (source.isEmpty || source.head != '{') {
      return Left("malformed object")
    }

    val fields = mutable.LinkedHashMap.empty[String, FlatJsonValue]
    var index = skipWhitespace(source, 1)

    if (index < source.length && source.charAt(index) == '}') {
      index += 1
      index = skipWhitespace(source, index)
      return if (index == source.length) Right(fields.toMap) else Left("trailing content after object")
    }

    while (index < source.length) {
      parseJsonString(source, index) match {
        case Left(error) =>
          return Left(error)
        case Right((key, nextIndex)) =>
          index = skipWhitespace(source, nextIndex)
          if (index >= source.length || source.charAt(index) != ':') {
            return Left("malformed object field separator")
          }

          index = skipWhitespace(source, index + 1)
          parseFlatValue(source, index) match {
            case Left(error) =>
              return Left(error)
            case Right((value, nextValueIndex)) =>
              fields.update(key, value)
              index = skipWhitespace(source, nextValueIndex)
              if (index >= source.length) {
                return Left("unterminated object")
              }

              source.charAt(index) match {
                case ',' =>
                  index = skipWhitespace(source, index + 1)
                case '}' =>
                  index += 1
                  index = skipWhitespace(source, index)
                  return if (index == source.length) Right(fields.toMap) else Left("trailing content after object")
                case _ =>
                  return Left("malformed object delimiter")
              }
          }
      }
    }

    Left("unterminated object")
  }

  private def parseFlatValue(source: String, start: Int): Either[String, (FlatJsonValue, Int)] = {
    if (start >= source.length) {
      Left("missing value")
    } else {
      source.charAt(start) match {
        case '"' =>
          parseJsonString(source, start).map { case (value, nextIndex) =>
            (FlatJsonValue.JsonString(value), nextIndex)
          }
        case 'n' =>
          parseLiteral(source, start, "null").map(nextIndex => (FlatJsonValue.JsonNull, nextIndex))
        case 't' =>
          parseLiteral(source, start, "true").map(nextIndex => (FlatJsonValue.JsonBoolean(true), nextIndex))
        case 'f' =>
          parseLiteral(source, start, "false").map(nextIndex => (FlatJsonValue.JsonBoolean(false), nextIndex))
        case value if value == '-' || value.isDigit =>
          parseJsonNumber(source, start).map { case (number, nextIndex) =>
            (FlatJsonValue.JsonNumber(number), nextIndex)
          }
        case _ =>
          Left("unsupported non-scalar field in replay catalog")
      }
    }
  }

  private def parseJsonString(source: String, start: Int): Either[String, (String, Int)] = {
    if (start >= source.length || source.charAt(start) != '"') {
      return Left("expected json string")
    }

    val builder = new StringBuilder
    var index = start + 1

    while (index < source.length) {
      source.charAt(index) match {
        case '"' =>
          return Right((builder.result(), index + 1))
        case '\\' =>
          index += 1
          if (index >= source.length) {
            return Left("unterminated string escape")
          }

          source.charAt(index) match {
            case '"'  => builder += '"'
            case '\\' => builder += '\\'
            case '/'  => builder += '/'
            case 'b'  => builder += '\b'
            case 'f'  => builder += '\f'
            case 'n'  => builder += '\n'
            case 'r'  => builder += '\r'
            case 't'  => builder += '\t'
            case 'u' =>
              if (index + 4 >= source.length) {
                return Left("invalid unicode escape")
              }

              val hex = source.substring(index + 1, index + 5)
              if (!hex.forall(isHexDigit)) {
                return Left("invalid unicode escape")
              }

              builder += Integer.parseInt(hex, 16).toChar
              index += 4
            case _ =>
              return Left("invalid string escape")
          }
        case value if Character.isISOControl(value) =>
          return Left("invalid control character in string")
        case value =>
          builder += value
      }

      index += 1
    }

    Left("unterminated string")
  }

  private def parseLiteral(source: String, start: Int, expected: String): Either[String, Int] = {
    if (source.regionMatches(start, expected, 0, expected.length)) {
      Right(start + expected.length)
    } else {
      Left("invalid literal")
    }
  }

  private def parseJsonNumber(source: String, start: Int): Either[String, (String, Int)] = {
    var index = start
    if (source.charAt(index) == '-') {
      index += 1
      if (index >= source.length) {
        return Left("invalid number")
      }
    }

    if (source.charAt(index) == '0') {
      index += 1
    } else if (source.charAt(index).isDigit) {
      while (index < source.length && source.charAt(index).isDigit) {
        index += 1
      }
    } else {
      return Left("invalid number")
    }

    if (index < source.length && source.charAt(index) == '.') {
      index += 1
      if (index >= source.length || !source.charAt(index).isDigit) {
        return Left("invalid number")
      }
      while (index < source.length && source.charAt(index).isDigit) {
        index += 1
      }
    }

    if (index < source.length && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
      index += 1
      if (index < source.length && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
        index += 1
      }
      if (index >= source.length || !source.charAt(index).isDigit) {
        return Left("invalid number")
      }
      while (index < source.length && source.charAt(index).isDigit) {
        index += 1
      }
    }

    Right((source.substring(start, index), index))
  }

  private def previewStringField(raw: String, field: String): Option[String] = {
    val fieldToken = jsonString(field)
    val marker = raw.indexOf(fieldToken)
    if (marker < 0) {
      None
    } else {
      val separator = raw.indexOf(':', marker + fieldToken.length)
      if (separator < 0) {
        None
      } else {
        val valueStart = skipWhitespace(raw, separator + 1)
        if (valueStart >= raw.length || raw.charAt(valueStart) != '"') {
          None
        } else {
          parseJsonString(raw, valueStart).toOption.map(_._1)
        }
      }
    }
  }

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def jsonString(value: String): String = s"\"${escape(value)}\""

  private def extractString(fields: Map[String, FlatJsonValue], field: String): Option[String] =
    fields.get(field).collect {
      case FlatJsonValue.JsonString(value) => value
    }

  private def extractNullableString(fields: Map[String, FlatJsonValue], field: String): Option[Option[String]] =
    fields.get(field) match {
      case Some(FlatJsonValue.JsonNull) => Some(None)
      case Some(FlatJsonValue.JsonString(value)) => Some(Some(value))
      case _ => None
    }

  private def extractInt(fields: Map[String, FlatJsonValue], field: String): Option[Int] =
    fields.get(field).collect {
      case FlatJsonValue.JsonNumber(rawValue) => rawValue.toInt
    }

  private def extractNullableInt(fields: Map[String, FlatJsonValue], field: String): Option[Int] =
    fields.get(field) match {
      case Some(FlatJsonValue.JsonNull) => None
      case Some(FlatJsonValue.JsonNumber(rawValue)) => Some(rawValue.toInt)
      case _ => None
    }

  private def extractLong(fields: Map[String, FlatJsonValue], field: String): Option[Long] =
    fields.get(field).collect {
      case FlatJsonValue.JsonNumber(rawValue) => rawValue.toLong
    }

  private def extractBoolean(fields: Map[String, FlatJsonValue], field: String): Option[Boolean] =
    fields.get(field).collect {
      case FlatJsonValue.JsonBoolean(value) => value
    }

  private def skipWhitespace(raw: String, start: Int): Int = {
    var index = start
    while (index < raw.length && raw.charAt(index).isWhitespace) {
      index += 1
    }
    index
  }

  private def findNextArraySeparator(raw: String, start: Int): Int = {
    var index = start
    var inString = false
    var escaped = false

    while (index < raw.length) {
      val ch = raw.charAt(index)
      if (inString) {
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '"') {
          inString = false
        }
      } else if (ch == '"') {
        inString = true
      } else if (ch == ',') {
        return index + 1
      }

      index += 1
    }

    index
  }

  private def isHexDigit(value: Char): Boolean = {
    (value >= '0' && value <= '9') ||
    (value >= 'a' && value <= 'f') ||
    (value >= 'A' && value <= 'F')
  }

  private def normalize(replayId: String): String = replayId.trim.toLowerCase
}
