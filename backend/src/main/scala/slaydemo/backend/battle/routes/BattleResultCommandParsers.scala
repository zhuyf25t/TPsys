package slaydemo.backend.battle.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import slaydemo.backend.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, Rating, RatingDelta, Score}
import slaydemo.backend.battle.services.results.BattleResultRecordCommand
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.shared.policies.HandlePolicy

private[routes] object BattleResultCommandParsers {
  def parseListRequest(rawQuery: String): BattleResultListRequestParseResult = {
    val query = queryParams(rawQuery)
    val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(25)
    val handleFilter = query.get("handle").flatMap(nonEmptyText) match {
      case None =>
        None
      case Some(rawHandle) =>
        PlayerHandle.forLookup(rawHandle) match {
          case None =>
            return BattleResultListRequestParseResult.EmptyResults
          case Some(handle) =>
            Some(handle)
        }
    }
    val battleIdFilter = query.get("battleId").flatMap(nonEmptyText).map(BattleId.apply)
    BattleResultListRequestParseResult.Query(BattleResultListRequest(handleFilter, battleIdFilter, limit))
  }

  def parseRecordCommand(
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
      placement = readOptionalInt(fields, "placement").flatMap(BattlePlacement.fromWire),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(readBoolean(fields, "aliveAtEnd").getOrElse(false)),
      ratingBefore = Rating(readInt(fields, "ratingBefore").getOrElse(0)),
      ratingDelta = RatingDelta(readInt(fields, "ratingDelta").getOrElse(0)),
      ratingAfter = Rating(readInt(fields, "ratingAfter").getOrElse(0)),
      resultLabel = readString(fields, "resultLabel").getOrElse(""),
      modeLabel = readString(fields, "modeLabel").getOrElse(""),
      mapLabel = readString(fields, "mapLabel").getOrElse(""),
      highlightLine = readString(fields, "highlightLine").getOrElse(""),
      playersLine = readString(fields, "playersLine").getOrElse(""),
      timelineHint = readString(fields, "timelineHint").getOrElse(""),
      currentLoadout = readNullableString(fields, "currentLoadout")
    )

  private def queryParams(rawQuery: String): Map[String, String] =
    Option(rawQuery).toVector
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

  private def parseSubmissionHandle(value: String): Either[BattleResultRecordCommandParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(BattleResultRecordCommandParseError.InvalidHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(BattleResultRecordCommandParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(BattleResultRecordCommandParseError.InvalidHandle)
  }

  private def isWholeInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def isWholeLong(value: Double): Boolean =
    value.isWhole && value >= Long.MinValue.toDouble && value <= Long.MaxValue.toDouble

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

private[routes] final case class BattleResultListRequest(
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: Int
)

private[routes] enum BattleResultListRequestParseResult {
  case Query(request: BattleResultListRequest)
  case EmptyResults
}

private[routes] enum BattleResultRecordCommandParseError {
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}
