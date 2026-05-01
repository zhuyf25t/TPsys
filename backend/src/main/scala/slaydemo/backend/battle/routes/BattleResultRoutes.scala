package slaydemo.backend.battle.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.objects.{BattleId, BattleResultRecord, DurationMillis, EpochMillis, Rating, Score}
import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.shared.routes.HttpRouteSupport

final class BattleResultRoutes(service: BattleResultService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" =>
          handleList(exchange)
        case "POST" =>
          handleRecord(exchange)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET, POST, HEAD, and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def handleList(exchange: HttpExchange): Unit = {
    val query = queryParams(exchange)
    val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(25)
    val handleFilter = query.get("handle").flatMap(nonEmptyText) match {
      case None =>
        None
      case Some(rawHandle) =>
        PlayerHandle.forLookup(rawHandle) match {
          case None =>
            HttpRouteSupport.sendJson(exchange, 200, renderRecords(Vector.empty))
            return
          case Some(handle) =>
            Some(handle)
        }
    }
    val battleIdFilter = query.get("battleId").flatMap(nonEmptyText).map(BattleId.apply)
    val results = service.list(
      handle = handleFilter,
      battleId = battleIdFilter,
      limit = limit
    )
    HttpRouteSupport.sendJson(exchange, 200, renderRecords(results))
  }

  private def handleRecord(exchange: HttpExchange): Unit =
    ResultJsonObjectParser.parse(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object.")
      case Right(fields) =>
        parseRecordCommand(fields) match {
          case Right(command) =>
            val record = service.record(command)
            HttpRouteSupport.sendJson(exchange, 201, renderRecord(record))
          case Left(BattleResultRecordCommandParseError.InvalidBattleId) =>
            jsonError(exchange, 400, "invalid_battle_id", "invalid_battle_id")
          case Left(BattleResultRecordCommandParseError.InvalidHandle) =>
            jsonError(exchange, 400, "invalid_handle", "invalid_handle")
          case Left(BattleResultRecordCommandParseError.VisitorNotAllowed) =>
            jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
        }
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

  private def readString(fields: Map[String, ResultJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ResultJsonValue.StringValue(value)) => Some(value)
      case Some(ResultJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(ResultJsonValue.NumberValue(value)) => Some(value.toString)
      case Some(ResultJsonValue.BooleanValue(value)) => Some(value.toString)
      case _ => None
    }

  private def readNullableString(fields: Map[String, ResultJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ResultJsonValue.NullValue) => None
      case _ => readString(fields, key).map(_.trim).filter(value => value.nonEmpty && value != "null")
    }

  private def readLong(fields: Map[String, ResultJsonValue], key: String): Option[Long] =
    fields.get(key) match {
      case Some(ResultJsonValue.StringValue(value)) => value.trim.toLongOption
      case Some(ResultJsonValue.NumberValue(value)) if isWholeLong(value) => Some(value.toLong)
      case _ => None
    }

  private def readInt(fields: Map[String, ResultJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(ResultJsonValue.StringValue(value)) => value.trim.toIntOption
      case Some(ResultJsonValue.NumberValue(value)) if isWholeInt(value) => Some(value.toInt)
      case _ => None
    }

  private def readOptionalInt(fields: Map[String, ResultJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(ResultJsonValue.NullValue) | None => None
      case _ => readInt(fields, key)
    }

  private def readBoolean(fields: Map[String, ResultJsonValue], key: String): Option[Boolean] =
    fields.get(key) match {
      case Some(ResultJsonValue.BooleanValue(value)) => Some(value)
      case Some(ResultJsonValue.StringValue(value)) =>
        value.trim.toLowerCase(Locale.ROOT) match {
          case "true"  => Some(true)
          case "false" => Some(false)
          case _       => None
        }
      case _ => None
    }

  private def parseRecordCommand(
    fields: Map[String, ResultJsonValue]
  ): Either[BattleResultRecordCommandParseError, BattleResultRecordCommand] =
    for {
      battleId <- nonEmptyText(readString(fields, "battleId").getOrElse(""))
        .map(BattleId.apply)
        .toRight(BattleResultRecordCommandParseError.InvalidBattleId)
      handle <- parseSubmissionHandle(readString(fields, "handle").getOrElse(""))
    } yield BattleResultRecordCommand(
      battleId = battleId,
      handle = handle,
      displayName = DisplayName(nonEmptyText(readString(fields, "displayName").getOrElse("")).getOrElse(handle.value)),
      finishedAt = EpochMillis(math.max(0L, readLong(fields, "finishedAt").getOrElse(0L))),
      finishedAtLabel = readString(fields, "finishedAtLabel").getOrElse(""),
      durationMs = DurationMillis(math.max(0L, readLong(fields, "durationMs").getOrElse(0L))),
      score = Score(math.max(0, readInt(fields, "score").getOrElse(0))),
      placement = readOptionalInt(fields, "placement").filter(_ > 0),
      aliveAtEnd = readBoolean(fields, "aliveAtEnd").getOrElse(false),
      ratingBefore = Rating(readInt(fields, "ratingBefore").getOrElse(0)),
      ratingDelta = readInt(fields, "ratingDelta").getOrElse(0),
      ratingAfter = Rating(readInt(fields, "ratingAfter").getOrElse(0)),
      resultLabel = readString(fields, "resultLabel").getOrElse(""),
      modeLabel = readString(fields, "modeLabel").getOrElse(""),
      mapLabel = readString(fields, "mapLabel").getOrElse(""),
      highlightLine = readString(fields, "highlightLine").getOrElse(""),
      playersLine = readString(fields, "playersLine").getOrElse(""),
      timelineHint = readString(fields, "timelineHint").getOrElse(""),
      currentLoadout = readNullableString(fields, "currentLoadout")
    )

  private def parseSubmissionHandle(value: String): Either[BattleResultRecordCommandParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(BattleResultRecordCommandParseError.InvalidHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(BattleResultRecordCommandParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(BattleResultRecordCommandParseError.InvalidHandle)
  }

  private def renderRecords(records: Vector[BattleResultRecord]): String =
    renderObject(Vector("results" -> records.map(renderRecord).mkString("[", ",", "]")))

  private def renderRecord(record: BattleResultRecord): String =
    renderObject(
      Vector(
        "resultId" -> jsonString(record.resultId.value),
        "battleId" -> jsonString(record.battleId.value),
        "handle" -> jsonString(record.handle.value),
        "displayName" -> jsonString(record.displayName.value),
        "finishedAt" -> record.finishedAt.value.toString,
        "finishedAtLabel" -> jsonString(record.finishedAtLabel),
        "durationMs" -> record.durationMs.value.toString,
        "score" -> record.score.value.toString,
        "placement" -> record.placement.map(_.toString).getOrElse("null"),
        "aliveAtEnd" -> record.aliveAtEnd.toString,
        "ratingBefore" -> record.ratingBefore.value.toString,
        "ratingDelta" -> record.ratingDelta.toString,
        "ratingAfter" -> record.ratingAfter.value.toString,
        "resultLabel" -> jsonString(record.resultLabel),
        "modeLabel" -> jsonString(record.modeLabel),
        "mapLabel" -> jsonString(record.mapLabel),
        "highlightLine" -> jsonString(record.highlightLine),
        "playersLine" -> jsonString(record.playersLine),
        "timelineHint" -> jsonString(record.timelineHint),
        "currentLoadout" -> record.currentLoadout.map(jsonString).getOrElse("null")
      )
    )

  private def isWholeInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def isWholeLong(value: Double): Boolean =
    value.isWhole && value >= Long.MinValue.toDouble && value <= Long.MaxValue.toDouble

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(
      exchange,
      status,
      s"""{"error":${jsonString(message)},"code":${jsonString(code)}}"""
    )
}

object BattleResultRoutes {
  def apply(service: BattleResultService): BattleResultRoutes =
    new BattleResultRoutes(service)
}

private enum ResultJsonValue {
  case StringValue(value: String)
  case NumberValue(value: Double)
  case BooleanValue(value: Boolean)
  case NullValue
}

private enum BattleResultRecordCommandParseError {
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

private enum ResultJsonParseError {
  case ExpectedObject
  case ExpectedField
  case ExpectedValue
}

private object ResultJsonObjectParser {
  def parse(body: String): Either[ResultJsonParseError, Map[String, ResultJsonValue]] = {
    val trimmed = Option(body).getOrElse("").trim
    if trimmed.isEmpty then Right(Map.empty)
    else Parser(trimmed).parse()
  }

  private final class Parser(source: String) {
    def parse(): Either[ResultJsonParseError, Map[String, ResultJsonValue]] = {
      var index = skipWhitespace(0)
      if !hasChar(index, '{') then return Left(ResultJsonParseError.ExpectedObject)
      index = skipWhitespace(index + 1)

      var fields = Map.empty[String, ResultJsonValue]
      if hasChar(index, '}') then return Right(fields)

      while index < source.length do {
        parseString(index) match {
          case None =>
            return Left(ResultJsonParseError.ExpectedField)
          case Some((key, afterKey)) =>
            index = skipWhitespace(afterKey)
            if !hasChar(index, ':') then return Left(ResultJsonParseError.ExpectedField)
            index = skipWhitespace(index + 1)

            parseValue(index) match {
              case None =>
                return Left(ResultJsonParseError.ExpectedValue)
              case Some((value, afterValue)) =>
                fields = fields.updated(key, value)
                index = skipWhitespace(afterValue)
                if hasChar(index, '}') then return Right(fields)
                if !hasChar(index, ',') then return Left(ResultJsonParseError.ExpectedField)
                index = skipWhitespace(index + 1)
            }
        }
      }

      Left(ResultJsonParseError.ExpectedObject)
    }

    private def parseValue(start: Int): Option[(ResultJsonValue, Int)] =
      if hasChar(start, '"') then
        parseString(start).map { case (value, next) => ResultJsonValue.StringValue(value) -> next }
      else if startsWith(start, "null") then
        Some(ResultJsonValue.NullValue -> (start + 4))
      else if startsWith(start, "true") then
        Some(ResultJsonValue.BooleanValue(true) -> (start + 4))
      else if startsWith(start, "false") then
        Some(ResultJsonValue.BooleanValue(false) -> (start + 5))
      else parseNumber(start)

    private def parseNumber(start: Int): Option[(ResultJsonValue, Int)] = {
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
          .map(value => ResultJsonValue.NumberValue(value) -> index)
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
            case None =>
              return None
            case Some((decoded, nextIndex)) =>
              builder.append(decoded)
              index = nextIndex
              escaped = false
          }
        } else if char == '\\' then {
          escaped = true
          index += 1
        } else if char == '"' then {
          return Some(builder.result() -> (index + 1))
        } else {
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
      if !hex.forall(isHexDigit) then None
      else Some(Integer.parseInt(hex, 16).toChar -> end)
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
