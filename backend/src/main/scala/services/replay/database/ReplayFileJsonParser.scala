package services.replay.database

import services.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, Rating, RatingDelta, Score}
import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.objects.{
  ReplayCommentId,
  ReplayCommentRecord,
  ReplayFrameCount,
  ReplayFramesJson,
  ReplayId,
  ReplayPlaybackAvailability,
  ReplayRecord,
  ReplaySettlementRecord,
  ReplayTitle
}
import services.replay.support.ReplayFramesJsonCodec

private[database] final case class ReplayFilePayload(
  records: Vector[ReplayRecord],
  comments: Vector[ReplayCommentRecord],
  settlementsByReplay: Map[ReplayId, Vector[ReplaySettlementRecord]]
)

private[database] object ReplayFileJsonParser {
  def parse(raw: String): ReplayFilePayload = {
    val settlementsByReplay = ReplayFileJsonObjectScanner.extractArrayObjects(raw, "settlements")
      .flatMap(parseSettlement)
      .groupMap(_._1)(_._2)

    ReplayFilePayload(
      records = ReplayFileJsonObjectScanner.extractArrayObjects(raw, "records").flatMap(parseReplay),
      comments = ReplayFileJsonObjectScanner.extractArrayObjects(raw, "comments").flatMap(parseComment),
      settlementsByReplay = settlementsByReplay
    )
  }

  private def parseReplay(chunk: String): Option[ReplayRecord] =
    for {
      replayId <- extractString(chunk, "replayId")
      battleId <- extractString(chunk, "battleId")
      handle <- extractString(chunk, "handle")
      displayName <- extractString(chunk, "displayName")
      finishedAt <- extractLong(chunk, "finishedAt")
      finishedAtLabel <- extractString(chunk, "finishedAtLabel")
      title <- extractString(chunk, "title")
      modeLabel <- extractString(chunk, "modeLabel")
      resultLabel <- extractString(chunk, "resultLabel")
      mapLabel <- extractString(chunk, "mapLabel")
      highlightLine <- extractString(chunk, "highlightLine")
      coverLabel <- extractString(chunk, "coverLabel")
      playersLine <- extractString(chunk, "playersLine")
      timelineHint <- extractString(chunk, "timelineHint")
      score <- extractInt(chunk, "score")
      durationMs <- extractLong(chunk, "durationMs")
      aliveAtEnd <- extractBoolean(chunk, "aliveAtEnd")
      frameCount <- extractInt(chunk, "frameCount")
      playbackAvailable <- extractBoolean(chunk, "playbackAvailable")
      framesJsonB64 <- extractString(chunk, "framesJsonB64")
    } yield ReplayRecord(
      replayId = ReplayId(replayId),
      battleId = BattleId(battleId),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      finishedAt = EpochMillis(finishedAt),
      finishedAtLabel = finishedAtLabel,
      title = ReplayTitle.fromWire(title),
      modeLabel = modeLabel,
      resultLabel = resultLabel,
      mapLabel = mapLabel,
      highlightLine = highlightLine,
      coverLabel = coverLabel,
      playersLine = playersLine,
      timelineHint = timelineHint,
      score = Score(score),
      placement = extractNullableInt(chunk, "placement").flatMap(BattlePlacement.fromWire),
      ratingBefore = extractNullableInt(chunk, "ratingBefore").map(Rating.apply),
      ratingDelta = extractNullableInt(chunk, "ratingDelta").map(RatingDelta.apply),
      ratingAfter = extractNullableInt(chunk, "ratingAfter").map(Rating.apply),
      durationMs = DurationMillis(durationMs),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd),
      thumbnailDataUrl = extractNullableString(chunk, "thumbnailDataUrl"),
      currentLoadout = extractNullableString(chunk, "currentLoadout"),
      frameCount = ReplayFrameCount.fromWire(frameCount),
      playbackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(playbackAvailable),
      framesJson = ReplayFramesJson.fromNormalized(ReplayFramesJsonCodec.decode(framesJsonB64))
    )

  private def parseComment(chunk: String): Option[ReplayCommentRecord] =
    for {
      id <- extractString(chunk, "id")
      replayId <- extractString(chunk, "replayId")
      authorHandle <- extractString(chunk, "authorHandle")
      body <- extractString(chunk, "body")
      createdAt <- extractLong(chunk, "createdAt")
    } yield ReplayCommentRecord(
      id = ReplayCommentId(id),
      replayId = ReplayId(replayId),
      authorHandle = PlayerHandle(authorHandle),
      body = body,
      createdAt = EpochMillis(createdAt)
    )

  private def parseSettlement(chunk: String): Option[(ReplayId, ReplaySettlementRecord)] =
    for {
      replayId <- extractString(chunk, "replayId")
      handle <- extractString(chunk, "handle")
      displayName <- extractString(chunk, "displayName")
      resultLabel <- extractString(chunk, "resultLabel")
      highlightLine <- extractString(chunk, "highlightLine")
      score <- extractInt(chunk, "score")
      aliveAtEnd <- extractBoolean(chunk, "aliveAtEnd")
    } yield ReplayId(replayId) -> ReplaySettlementRecord(
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      resultLabel = resultLabel,
      highlightLine = highlightLine,
      score = Score(score),
      placement = extractNullableInt(chunk, "placement").flatMap(BattlePlacement.fromWire),
      ratingBefore = extractNullableInt(chunk, "ratingBefore").map(Rating.apply),
      ratingDelta = extractNullableInt(chunk, "ratingDelta").map(RatingDelta.apply),
      ratingAfter = extractNullableInt(chunk, "ratingAfter").map(Rating.apply),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd),
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
