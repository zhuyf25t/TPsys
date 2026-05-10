package slaydemo.backend.battle.database

import slaydemo.backend.battle.objects.{
  BattleId,
  BattleHighlightLine,
  BattleMapLabel,
  BattleModeLabel,
  BattleResultRecord,
  BattleResultLabel,
  BattlePlayersLine,
  BattlePlacement,
  BattleTimelineHint,
  BattleSurvivalOutcome,
  DurationMillis,
  EpochMillis,
  Rating,
  RatingDelta,
  Score
}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

private[database] object BattleResultFileJsonParser {
  def parseRecords(raw: String): Vector[BattleResultRecord] =
    extractResultObjects(raw).flatMap(parseRecord)

  private def extractResultObjects(raw: String): Vector[String] = {
    val marker = raw.indexOf("\"results\"")
    if marker < 0 then Vector.empty
    else {
      val start = raw.indexOf('[', marker)
      val end = raw.lastIndexOf(']')
      if start < 0 || end < 0 || end <= start then Vector.empty
      else "\\{([^{}]*)\\}".r.findAllMatchIn(raw.substring(start + 1, end)).map(_.group(1)).toVector
    }
  }

  private def parseRecord(chunk: String): Option[BattleResultRecord] =
    for {
      battleId <- extractString(chunk, "battleId")
      handle <- extractString(chunk, "handle")
      displayName <- extractString(chunk, "displayName")
      finishedAt <- extractLong(chunk, "finishedAt")
      finishedAtLabel <- extractString(chunk, "finishedAtLabel")
      durationMs <- extractLong(chunk, "durationMs")
      score <- extractInt(chunk, "score")
      aliveAtEnd <- extractBoolean(chunk, "aliveAtEnd")
      ratingBefore <- extractInt(chunk, "ratingBefore")
      ratingDelta <- extractInt(chunk, "ratingDelta")
      ratingAfter <- extractInt(chunk, "ratingAfter")
      resultLabel <- extractString(chunk, "resultLabel")
      modeLabel <- extractString(chunk, "modeLabel")
      mapLabel <- extractString(chunk, "mapLabel")
      highlightLine <- extractString(chunk, "highlightLine")
      playersLine <- extractString(chunk, "playersLine")
      timelineHint <- extractString(chunk, "timelineHint")
    } yield BattleResultRecord(
      battleId = BattleId(battleId),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      finishedAt = EpochMillis(finishedAt),
      finishedAtLabel = finishedAtLabel,
      durationMs = DurationMillis(durationMs),
      score = Score(score),
      placement = extractNullableInt(chunk, "placement").flatMap(BattlePlacement.fromWire),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd),
      ratingBefore = Rating(ratingBefore),
      ratingDelta = RatingDelta(ratingDelta),
      ratingAfter = Rating(ratingAfter),
      resultLabel = BattleResultLabel.fromWire(resultLabel),
      modeLabel = BattleModeLabel.fromWire(modeLabel),
      mapLabel = BattleMapLabel.fromWire(mapLabel),
      highlightLine = BattleHighlightLine.fromWire(highlightLine),
      playersLine = BattlePlayersLine.fromWire(playersLine),
      timelineHint = BattleTimelineHint.fromWire(timelineHint),
      currentLoadout = extractNullableString(chunk, "currentLoadout")
    )

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractNullableString(raw: String, field: String): Option[String] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if nullPattern.findFirstIn(raw).nonEmpty then None
    else extractString(raw, field).map(_.trim).filter(_.nonEmpty)
  }

  private def extractInt(raw: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toInt)
  }

  private def extractNullableInt(raw: String, field: String): Option[Int] = {
    val nullPattern = s""""$field"\\s*:\\s*null""".r
    if nullPattern.findFirstIn(raw).nonEmpty then None else extractInt(raw, field)
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toLong)
  }

  private def extractBoolean(raw: String, field: String): Option[Boolean] = {
    val pattern = s""""$field"\\s*:\\s*(true|false)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toBoolean)
  }

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")
}
