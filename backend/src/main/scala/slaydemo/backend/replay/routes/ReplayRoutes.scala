package slaydemo.backend.replay.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.objects.{BattleId, DurationMillis, EpochMillis, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayId, ReplayRecord, ReplaySettlementRecord}
import slaydemo.backend.replay.services.{ReplayCommentCommand, ReplayCommentError, ReplayIdentifierPolicy, ReplayRecordCommand, ReplayRecordError, ReplayService}
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.shared.routes.HttpRouteSupport

final class ReplayRoutes(service: ReplayService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      val target = parseTarget(exchange.getRequestURI.getPath)
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          if target == ReplayTarget.Invalid then HttpRouteSupport.sendEmpty(exchange, 404)
          else HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" =>
          handleGet(exchange, target)
        case "POST" =>
          handlePost(exchange, target)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def handleGet(exchange: HttpExchange, target: ReplayTarget): Unit =
    target match {
      case ReplayTarget.Collection =>
        val limit = queryParams(exchange).get("limit").flatMap(_.toIntOption).getOrElse(25)
        HttpRouteSupport.sendJson(exchange, 200, renderCatalog(service.list(limit), replayHandleFromQuery(exchange)))
      case ReplayTarget.Detail(replayId) =>
        service.load(replayId) match {
          case Some(record) => HttpRouteSupport.sendJson(exchange, 200, renderDetail(record, replayHandleFromQuery(exchange)))
          case None         => jsonError(exchange, 404, "replay_not_found", "replay_not_found")
        }
      case ReplayTarget.Comments(replayId) =>
        service.load(replayId) match {
          case None =>
            jsonError(exchange, 404, "replay_not_found", "replay_not_found")
          case Some(_) =>
            val limit = queryParams(exchange).get("limit").flatMap(_.toIntOption).getOrElse(50)
            HttpRouteSupport.sendJson(exchange, 200, renderComments(service.listComments(replayId, limit)))
        }
      case ReplayTarget.Invalid =>
        jsonError(exchange, 404, "replay_not_found", "replay_not_found")
      case ReplayTarget.InvalidReplayId =>
        jsonError(exchange, 400, "invalid_replay_id", "invalid_replay_id")
    }

  private def handlePost(exchange: HttpExchange, target: ReplayTarget): Unit =
    target match {
      case ReplayTarget.Collection =>
        handleCatalogPost(exchange)
      case ReplayTarget.Comments(replayId) =>
        handleCommentPost(exchange, replayId)
      case ReplayTarget.Detail(_) =>
        jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      case ReplayTarget.Invalid =>
        jsonError(exchange, 404, "replay_not_found", "replay_not_found")
      case ReplayTarget.InvalidReplayId =>
        jsonError(exchange, 400, "invalid_replay_id", "invalid_replay_id")
    }

  private def handleCatalogPost(exchange: HttpExchange): Unit =
    ReplayJsonObjectParser.parse(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object.")
      case Right(fields) =>
        val framesJson = readString(fields, "framesJson")
          .orElse(readRawJson(fields, "frames"))
          .getOrElse("[]")
        parseReplayRecordCommand(fields, framesJson) match {
          case Right(command) =>
            service.record(command) match {
              case Right(record) =>
                HttpRouteSupport.sendJson(exchange, 201, renderDetail(record, None))
              case Left(ReplayRecordError.InvalidReplayId) =>
                jsonError(exchange, 400, "invalid_replay_id", "invalid_replay_id")
              case Left(ReplayRecordError.InvalidFramesJson) =>
                jsonError(exchange, 400, "invalid_frames_json", "invalid_frames_json")
            }
          case Left(ReplayRecordCommandParseError.InvalidReplayId) =>
            jsonError(exchange, 400, "invalid_replay_id", "invalid_replay_id")
          case Left(ReplayRecordCommandParseError.InvalidBattleId) =>
            jsonError(exchange, 400, "invalid_battle_id", "invalid_battle_id")
          case Left(ReplayRecordCommandParseError.InvalidHandle) =>
            jsonError(exchange, 400, "invalid_handle", "invalid_handle")
          case Left(ReplayRecordCommandParseError.VisitorNotAllowed) =>
            jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
        }
    }

  private def handleCommentPost(exchange: HttpExchange, replayId: ReplayId): Unit =
    ReplayJsonObjectParser.parse(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object.")
      case Right(fields) =>
        parseReplayCommentCommand(replayId, fields) match {
          case Left(ReplayCommentCommandParseError.InvalidReplayId) =>
            jsonError(exchange, 400, "invalid_replay_id", "invalid_replay_id")
          case Left(ReplayCommentCommandParseError.InvalidAuthorHandle) =>
            jsonError(exchange, 400, "invalid_author_handle", "invalid_author_handle")
          case Left(ReplayCommentCommandParseError.VisitorNotAllowed) =>
            jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
          case Right(command) =>
            service.addComment(command) match {
              case Right(comment) =>
                HttpRouteSupport.sendJson(exchange, 201, renderComment(comment))
              case Left(ReplayCommentError.InvalidReplayId) =>
                jsonError(exchange, 400, "invalid_replay_id", "invalid_replay_id")
              case Left(ReplayCommentError.ReplayNotFound) =>
                jsonError(exchange, 404, "replay_not_found", "replay_not_found")
              case Left(ReplayCommentError.InvalidAuthor) =>
                jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
              case Left(ReplayCommentError.InvalidBody) =>
                jsonError(exchange, 400, "invalid_body", "invalid_body")
            }
        }
    }

  private def parseTarget(path: String): ReplayTarget = {
    val segments = routePath(path)
      .stripPrefix("/")
      .stripSuffix("/")
      .split("/", -1)
      .toVector
      .filter(_.nonEmpty)
      .map(decode)

    segments match {
      case Vector("replay", "catalog") =>
        ReplayTarget.Collection
      case Vector("replay", "catalog", replayId) if replayId.nonEmpty =>
        parseReplayId(replayId).map(ReplayTarget.Detail.apply).getOrElse(ReplayTarget.InvalidReplayId)
      case Vector("replay", "catalog", replayId, "comments") if replayId.nonEmpty =>
        parseReplayId(replayId).map(ReplayTarget.Comments.apply).getOrElse(ReplayTarget.InvalidReplayId)
      case _ =>
        ReplayTarget.Invalid
    }
  }

  private def routePath(path: String): String = {
    val raw = Option(path).getOrElse("")
    if raw == "/api" then "/"
    else if raw.startsWith("/api/") then raw.stripPrefix("/api")
    else raw
  }

  private def queryParams(exchange: HttpExchange): Map[String, String] =
    Option(exchange.getRequestURI.getRawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def renderCatalog(records: Vector[ReplayRecord], selectedHandle: Option[PlayerHandle]): String =
    renderObject(Vector("replays" -> records.map(renderCatalogRecord(_, selectedHandle)).mkString("[", ",", "]")))

  private def renderDetail(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): String =
    renderObject(Vector("replay" -> renderDetailRecord(record, selectedHandle)))

  private def renderCatalogRecord(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): String =
    renderObject(catalogFields(record, selectedSettlement(record, selectedHandle)))

  private def renderDetailRecord(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): String = {
    val settlement = selectedSettlement(record, selectedHandle)
    renderObject(
      catalogFields(record, settlement) ++ Vector(
        "handle" -> jsonString(settlement.map(_.handle.value).getOrElse(record.handle.value)),
        "displayName" -> jsonString(settlement.map(_.displayName.value).getOrElse(record.displayName.value)),
        "currentLoadout" -> renderOptionalString(settlement.flatMap(_.currentLoadout).orElse(record.currentLoadout)),
        "frames" -> record.framesJson
      )
    )
  }

  private def catalogFields(record: ReplayRecord, settlement: Option[ReplaySettlementRecord]): Vector[(String, String)] = {
    val resultLabel = settlement.map(_.resultLabel).getOrElse(record.resultLabel)
    Vector(
      "replayId" -> jsonString(record.replayId.value),
      "battleId" -> jsonString(record.battleId.value),
      "title" -> jsonString(settlement.map(item => s"${item.resultLabel} - ${record.finishedAtLabel}").getOrElse(record.title)),
      "modeLabel" -> jsonString(record.modeLabel),
      "resultLabel" -> jsonString(resultLabel),
      "finishedAt" -> record.finishedAt.value.toString,
      "finishedAtLabel" -> jsonString(record.finishedAtLabel),
      "mapLabel" -> jsonString(record.mapLabel),
      "highlightLine" -> jsonString(settlement.map(_.highlightLine).getOrElse(record.highlightLine)),
      "coverLabel" -> jsonString(record.coverLabel),
      "playersLine" -> jsonString(record.playersLine),
      "timelineHint" -> jsonString(record.timelineHint),
      "score" -> settlement.map(_.score.value).getOrElse(record.score.value).toString,
      "placement" -> settlement.map(_.placement).getOrElse(record.placement).map(_.toString).getOrElse("null"),
      "ratingBefore" -> settlement.map(_.ratingBefore).getOrElse(record.ratingBefore).map(_.value.toString).getOrElse("null"),
      "ratingAfter" -> settlement.map(_.ratingAfter).getOrElse(record.ratingAfter).map(_.value.toString).getOrElse("null"),
      "ratingDelta" -> settlement.map(_.ratingDelta).getOrElse(record.ratingDelta).map(_.toString).getOrElse("null"),
      "durationMs" -> record.durationMs.value.toString,
      "aliveAtEnd" -> settlement.map(_.aliveAtEnd).getOrElse(record.aliveAtEnd).toString,
      "thumbnailDataUrl" -> renderOptionalString(record.thumbnailDataUrl),
      "frameCount" -> record.frameCount.toString,
      "playbackAvailable" -> record.playbackAvailable.toString
    )
  }

  private def replayHandleFromQuery(exchange: HttpExchange): Option[PlayerHandle] =
    queryParams(exchange).get("handle").flatMap(PlayerHandle.forLookup)

  private def selectedSettlement(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): Option[ReplaySettlementRecord] =
    selectedHandle.flatMap(record.settlementFor)

  private def parseReplayRecordCommand(
    fields: Map[String, ReplayJsonValue],
    framesJson: String
  ): Either[ReplayRecordCommandParseError, ReplayRecordCommand] =
    for {
      replayId <- parseReplayId(readString(fields, "replayId").getOrElse(""))
        .toRight(ReplayRecordCommandParseError.InvalidReplayId)
      battleId <- parseBattleId(readString(fields, "battleId").getOrElse(""))
        .toRight(ReplayRecordCommandParseError.InvalidBattleId)
      handle <- parseRecordHandle(readString(fields, "handle").getOrElse(""))
    } yield {
      val frameCount = math.max(0, readInt(fields, "frameCount").getOrElse(0))
      ReplayRecordCommand(
        replayId = replayId,
        battleId = battleId,
        handle = handle,
        displayName = DisplayName(nonEmpty(readString(fields, "displayName").getOrElse("")).getOrElse(handle.value)),
        finishedAt = EpochMillis(math.max(0L, readLong(fields, "finishedAt").getOrElse(0L))),
        finishedAtLabel = readString(fields, "finishedAtLabel").getOrElse(""),
        title = readString(fields, "title").getOrElse(""),
        modeLabel = readString(fields, "modeLabel").getOrElse(""),
        resultLabel = readString(fields, "resultLabel").getOrElse(""),
        mapLabel = readString(fields, "mapLabel").getOrElse(""),
        highlightLine = readString(fields, "highlightLine").getOrElse(""),
        coverLabel = readString(fields, "coverLabel").getOrElse(""),
        playersLine = readString(fields, "playersLine").getOrElse(""),
        timelineHint = readString(fields, "timelineHint").getOrElse(""),
        score = Score(math.max(0, readInt(fields, "score").getOrElse(0))),
        placement = readOptionalInt(fields, "placement").filter(_ > 0),
        durationMs = DurationMillis(math.max(0L, readLong(fields, "durationMs").getOrElse(0L))),
        aliveAtEnd = readBoolean(fields, "aliveAtEnd").getOrElse(false),
        thumbnailDataUrl = readNullableString(fields, "thumbnailDataUrl"),
        currentLoadout = readNullableString(fields, "currentLoadout"),
        frameCount = frameCount,
        playbackAvailable = readBoolean(fields, "playbackAvailable").getOrElse(false),
        framesJson = framesJson
      )
    }

  private def parseReplayCommentCommand(
    replayId: ReplayId,
    fields: Map[String, ReplayJsonValue]
  ): Either[ReplayCommentCommandParseError, ReplayCommentCommand] =
    for {
      parsedReplayId <- parseReplayId(replayId.value).toRight(ReplayCommentCommandParseError.InvalidReplayId)
      author <- parseCommentHandle(readString(fields, "authorHandle").getOrElse(""))
    } yield ReplayCommentCommand(
      replayId = parsedReplayId,
      authorHandle = author,
      body = readString(fields, "body").getOrElse("")
    )

  private def parseReplayId(value: String): Option[ReplayId] =
    nonEmpty(value).filter(ReplayIdentifierPolicy.isSafeIdentifier).map(ReplayId.apply)

  private def parseBattleId(value: String): Option[BattleId] =
    nonEmpty(value).filter(_.length <= 200).map(BattleId.apply)

  private def parseRecordHandle(value: String): Either[ReplayRecordCommandParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ReplayRecordCommandParseError.InvalidHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(ReplayRecordCommandParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ReplayRecordCommandParseError.InvalidHandle)
  }

  private def parseCommentHandle(value: String): Either[ReplayCommentCommandParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ReplayCommentCommandParseError.InvalidAuthorHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(ReplayCommentCommandParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ReplayCommentCommandParseError.InvalidAuthorHandle)
  }

  private def renderComments(records: Vector[ReplayCommentRecord]): String =
    renderObject(Vector("comments" -> records.map(renderCommentRecord).mkString("[", ",", "]")))

  private def renderComment(comment: ReplayCommentRecord): String =
    renderObject(Vector("comment" -> renderCommentRecord(comment)))

  private def renderCommentRecord(comment: ReplayCommentRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(comment.id.value),
        "replayId" -> jsonString(comment.replayId.value),
        "authorHandle" -> jsonString(comment.authorHandle.value),
        "body" -> jsonString(comment.body),
        "createdAt" -> comment.createdAt.value.toString
      )
    )

  private def readString(fields: Map[String, ReplayJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ReplayJsonValue.StringValue(value)) => Some(value)
      case Some(ReplayJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(ReplayJsonValue.NumberValue(value)) => Some(value.toString)
      case Some(ReplayJsonValue.BooleanValue(value)) => Some(value.toString)
      case _ => None
    }

  private def readRawJson(fields: Map[String, ReplayJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ReplayJsonValue.RawJsonValue(value)) => Some(value)
      case _ => None
    }

  private def readNullableString(fields: Map[String, ReplayJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ReplayJsonValue.NullValue) | None => None
      case _ => readString(fields, key).map(_.trim).filter(value => value.nonEmpty && value != "null")
    }

  private def readLong(fields: Map[String, ReplayJsonValue], key: String): Option[Long] =
    fields.get(key) match {
      case Some(ReplayJsonValue.StringValue(value)) => value.trim.toLongOption
      case Some(ReplayJsonValue.NumberValue(value)) if isWholeLong(value) => Some(value.toLong)
      case _ => None
    }

  private def readInt(fields: Map[String, ReplayJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(ReplayJsonValue.StringValue(value)) => value.trim.toIntOption
      case Some(ReplayJsonValue.NumberValue(value)) if isWholeInt(value) => Some(value.toInt)
      case _ => None
    }

  private def readOptionalInt(fields: Map[String, ReplayJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(ReplayJsonValue.NullValue) | None => None
      case _ => readInt(fields, key)
    }

  private def readBoolean(fields: Map[String, ReplayJsonValue], key: String): Option[Boolean] =
    fields.get(key) match {
      case Some(ReplayJsonValue.BooleanValue(value)) => Some(value)
      case Some(ReplayJsonValue.StringValue(value)) =>
        value.trim.toLowerCase(Locale.ROOT) match {
          case "true"  => Some(true)
          case "false" => Some(false)
          case _       => None
        }
      case _ => None
    }

  private def isWholeInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def isWholeLong(value: Double): Boolean =
    value.isWhole && value >= Long.MinValue.toDouble && value <= Long.MaxValue.toDouble

  private def renderOptionalString(value: Option[String]): String =
    value.filter(_.trim.nonEmpty).map(jsonString).getOrElse("null")

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

object ReplayRoutes {
  def apply(service: ReplayService): ReplayRoutes =
    new ReplayRoutes(service)
}

private enum ReplayTarget {
  case Collection
  case Detail(replayId: ReplayId)
  case Comments(replayId: ReplayId)
  case Invalid
  case InvalidReplayId
}

private enum ReplayRecordCommandParseError {
  case InvalidReplayId
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

private enum ReplayCommentCommandParseError {
  case InvalidReplayId
  case InvalidAuthorHandle
  case VisitorNotAllowed
}

private enum ReplayJsonValue {
  case StringValue(value: String)
  case NumberValue(value: Double)
  case BooleanValue(value: Boolean)
  case RawJsonValue(value: String)
  case NullValue
}

private enum ReplayJsonParseError {
  case ExpectedObject
  case ExpectedField
  case ExpectedValue
}

private object ReplayJsonObjectParser {
  def parse(body: String): Either[ReplayJsonParseError, Map[String, ReplayJsonValue]] = {
    val trimmed = Option(body).getOrElse("").trim
    if trimmed.isEmpty then Right(Map.empty)
    else Parser(trimmed).parse()
  }

  private final class Parser(source: String) {
    def parse(): Either[ReplayJsonParseError, Map[String, ReplayJsonValue]] = {
      var index = skipWhitespace(0)
      if !hasChar(index, '{') then return Left(ReplayJsonParseError.ExpectedObject)
      index = skipWhitespace(index + 1)

      var fields = Map.empty[String, ReplayJsonValue]
      if hasChar(index, '}') then return Right(fields)

      while index < source.length do {
        parseString(index) match {
          case None => return Left(ReplayJsonParseError.ExpectedField)
          case Some((key, afterKey)) =>
            index = skipWhitespace(afterKey)
            if !hasChar(index, ':') then return Left(ReplayJsonParseError.ExpectedField)
            index = skipWhitespace(index + 1)

            parseValue(index) match {
              case None => return Left(ReplayJsonParseError.ExpectedValue)
              case Some((value, afterValue)) =>
                fields = fields.updated(key, value)
                index = skipWhitespace(afterValue)
                if hasChar(index, '}') then return Right(fields)
                if !hasChar(index, ',') then return Left(ReplayJsonParseError.ExpectedField)
                index = skipWhitespace(index + 1)
            }
        }
      }

      Left(ReplayJsonParseError.ExpectedObject)
    }

    private def parseValue(start: Int): Option[(ReplayJsonValue, Int)] =
      if hasChar(start, '"') then
        parseString(start).map { case (value, next) => ReplayJsonValue.StringValue(value) -> next }
      else if hasChar(start, '[') || hasChar(start, '{') then
        parseRawJson(start).map { case (value, next) => ReplayJsonValue.RawJsonValue(value) -> next }
      else if startsWith(start, "null") then
        Some(ReplayJsonValue.NullValue -> (start + 4))
      else if startsWith(start, "true") then
        Some(ReplayJsonValue.BooleanValue(true) -> (start + 4))
      else if startsWith(start, "false") then
        Some(ReplayJsonValue.BooleanValue(false) -> (start + 5))
      else parseNumber(start)

    private def parseRawJson(start: Int): Option[(String, Int)] = {
      val opening = source.charAt(start)
      val closing = if opening == '[' then ']' else '}'
      var index = start
      var depth = 0
      var inString = false
      var escaped = false

      while index < source.length do {
        val char = source.charAt(index)
        if inString then {
          if escaped then escaped = false
          else if char == '\\' then escaped = true
          else if char == '"' then inString = false
        } else if char == '"' then inString = true
        else if char == opening then depth += 1
        else if char == closing then {
          depth -= 1
          if depth == 0 then return Some(source.substring(start, index + 1) -> (index + 1))
        }
        index += 1
      }

      None
    }

    private def parseNumber(start: Int): Option[(ReplayJsonValue, Int)] = {
      var index = start
      if hasChar(index, '-') then index += 1
      val digitsStart = index
      while index < source.length && source.charAt(index).isDigit do index += 1
      if index == digitsStart then None
      else
        if hasChar(index, '.') then {
          index += 1
          val fractionStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == fractionStart then return None
        }
        val text = source.substring(start, index)
        text.toDoubleOption
          .filter(value => java.lang.Double.isFinite(value))
          .map(value => ReplayJsonValue.NumberValue(value) -> index)
    }

    private def parseString(start: Int): Option[(String, Int)] = {
      if !hasChar(start, '"') then return None
      val builder = StringBuilder()
      var index = start + 1
      var escaped = false

      while index < source.length do {
        val char = source.charAt(index)
        if escaped then {
          decodeEscaped(char, index) match {
            case None => return None
            case Some((decoded, nextIndex)) =>
              builder.append(decoded)
              index = nextIndex
              escaped = false
          }
        } else if char == '\\' then {
          escaped = true
          index += 1
        } else if char == '"' then return Some(builder.result() -> (index + 1))
        else {
          builder.append(char)
          index += 1
        }
      }

      None
    }

    private def decodeEscaped(char: Char, index: Int): Option[(Char, Int)] =
      char match {
        case '"'  => Some('"' -> (index + 1))
        case '\\' => Some('\\' -> (index + 1))
        case '/'  => Some('/' -> (index + 1))
        case 'b'  => Some('\b' -> (index + 1))
        case 'f'  => Some('\f' -> (index + 1))
        case 'n'  => Some('\n' -> (index + 1))
        case 'r'  => Some('\r' -> (index + 1))
        case 't'  => Some('\t' -> (index + 1))
        case 'u'  => decodeUnicodeEscape(index + 1)
        case _    => None
      }

    private def decodeUnicodeEscape(start: Int): Option[(Char, Int)] = {
      val end = start + 4
      if end > source.length then return None
      val hex = source.substring(start, end)
      if !hex.forall(isHexDigit) then None else Some(Integer.parseInt(hex, 16).toChar -> end)
    }

    private def skipWhitespace(start: Int): Int = {
      var index = start
      while index < source.length && source.charAt(index).isWhitespace do index += 1
      index
    }

    private def hasChar(index: Int, expected: Char): Boolean =
      index >= 0 && index < source.length && source.charAt(index) == expected

    private def startsWith(index: Int, expected: String): Boolean =
      source.regionMatches(index, expected, 0, expected.length)

    private def isHexDigit(char: Char): Boolean =
      (char >= '0' && char <= '9') ||
        (char >= 'a' && char <= 'f') ||
        (char >= 'A' && char <= 'F')
  }

  private object Parser {
    def apply(source: String): Parser =
      new Parser(source)
  }
}
