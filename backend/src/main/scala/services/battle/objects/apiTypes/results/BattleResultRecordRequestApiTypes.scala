package services.battle.objects.apiTypes.results

import io.circe.{Decoder, DecodingFailure, HCursor}

import services.battle.objects.core.{
  BattleHighlightLine,
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultLabel,
  BattleTimelineHint,
  DurationMillis,
  EpochMillis,
  Rating,
  RatingDelta,
  Score
}
import services.battle.objects.{BattleAPIRequestError, BattleResultRecordCommand}
import services.battle.objects.player.BattleSurvivalOutcome
import services.identity.objects.{DisplayName, PlayerHandle}
import system.policies.HandlePolicy

object BattleResultRecordRequest {
  given Decoder[BattleResultRecordCommand] =
    Decoder.instance(decodeRequest)

  private def parseSubmissionHandle(
    handle: Option[PlayerHandle],
    rejectVisitorLikeHandle: Boolean
  ): Either[DecodingFailure, PlayerHandle] =
    val trimmed = handle.map(value => HandlePolicy.trim(value.value)).getOrElse("")
    if trimmed.isEmpty then Left(DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.InvalidHandle), Nil))
    else if rejectVisitorLikeHandle && !HandlePolicy.isPlayableIdentityHandle(trimmed) then
      Left(DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.VisitorNotAllowed), Nil))
    else Right(PlayerHandle(trimmed))

  private def decodeRequest(cursor: HCursor): Either[DecodingFailure, BattleResultRecordCommand] =
    for
      battleId <- optionalText(cursor, "battleId").map(_.map(BattleId.apply))
      handle <- optionalText(cursor, "handle").map(_.map(PlayerHandle.apply))
      displayName <- optionalText(cursor, "displayName").map(_.map(DisplayName.apply))
      finishedAt <- optional[Long](cursor, "finishedAt").map(_.map(EpochMillis.apply))
      finishedAtLabel <- optional[String](cursor, "finishedAtLabel")
      durationMs <- optional[Long](cursor, "durationMs").map(_.map(DurationMillis.apply))
      score <- optional[Int](cursor, "score").map(_.map(Score.apply))
      placement <- optional[Int](cursor, "placement").map(_.flatMap(BattlePlacement.fromWire))
      aliveAtEnd <- optional[Boolean](cursor, "aliveAtEnd")
      ratingBefore <- optional[Int](cursor, "ratingBefore").map(_.map(Rating.apply))
      ratingDelta <- optional[Int](cursor, "ratingDelta").map(_.map(RatingDelta.apply))
      ratingAfter <- optional[Int](cursor, "ratingAfter").map(_.map(Rating.apply))
      resultLabel <- optionalText(cursor, "resultLabel").map(_.map(BattleResultLabel.fromWire))
      modeLabel <- optionalText(cursor, "modeLabel").map(_.map(BattleModeLabel.fromWire))
      mapLabel <- optionalText(cursor, "mapLabel").map(_.map(BattleMapLabel.fromWire))
      highlightLine <- optionalText(cursor, "highlightLine").map(_.map(BattleHighlightLine.fromWire))
      playersLine <- optionalText(cursor, "playersLine").map(_.map(BattlePlayersLine.fromWire))
      timelineHint <- optionalText(cursor, "timelineHint").map(_.map(BattleTimelineHint.fromWire))
      currentLoadout <- optionalText(cursor, "currentLoadout")
      requiredBattleId <- battleId.toRight(
        DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.InvalidBattleId), cursor.history)
      )
      parsedHandle <- parseSubmissionHandle(handle, rejectVisitorLikeHandle = false).left.map { failure =>
        DecodingFailure(failure.message, cursor.history)
      }
    yield BattleResultRecordCommand(
      battleId = requiredBattleId,
      handle = parsedHandle,
      displayName = displayName.getOrElse(DisplayName(parsedHandle.value)),
      finishedAt = EpochMillis(math.max(0L, finishedAt.map(_.value).getOrElse(0L))),
      finishedAtLabel = finishedAtLabel.getOrElse(""),
      durationMs = DurationMillis(math.max(0L, durationMs.map(_.value).getOrElse(0L))),
      score = Score(math.max(0, score.map(_.value).getOrElse(0))),
      placement = placement,
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd.getOrElse(false)),
      ratingBefore = ratingBefore.getOrElse(Rating(0)),
      ratingDelta = ratingDelta.getOrElse(RatingDelta(0)),
      ratingAfter = ratingAfter.getOrElse(Rating(0)),
      resultLabel = resultLabel.getOrElse(BattleResultLabel.fromWire("")),
      modeLabel = modeLabel.getOrElse(BattleModeLabel.fromWire("")),
      mapLabel = mapLabel.getOrElse(BattleMapLabel.fromWire("")),
      highlightLine = highlightLine.getOrElse(BattleHighlightLine.fromWire("")),
      playersLine = playersLine.getOrElse(BattlePlayersLine.fromWire("")),
      timelineHint = timelineHint.getOrElse(BattleTimelineHint.fromWire("")),
      currentLoadout = currentLoadout.filter(_ != "null")
    )

  private def optionalText(cursor: HCursor, key: String): Either[DecodingFailure, Option[String]] =
    optional[String](cursor, key).map(_.flatMap(nonEmpty))

  private def optional[A: Decoder](cursor: HCursor, key: String): Either[DecodingFailure, Option[A]] =
    cursor.get[Option[A]](key).left.map(_ => invalidField(key, cursor))

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def invalidField(key: String, cursor: HCursor): DecodingFailure =
    DecodingFailure(s"Invalid battle result record field: $key", cursor.history)
}
