package services.battle.microservices.results.api

import io.circe.Decoder

import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.battle.microservices.results.api.results.BattleResultRecordRequestDecodeError
import services.battle.microservices.results.objects.result.{
  BattleHighlightLine,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultFinishedAtLabel,
  BattleResultLabel,
  BattleResultListLimit,
  BattleResultLoadoutLabel,
  BattleTimelineHint,
  RatingDelta
}
import services.battle.objects.core.{
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  DurationMillis,
  EpochMillis
}
import services.identity.objects.{DisplayName, PlayerHandle}
import system.objects.UserId

private[api] object BattleResultAPIMessageDecoding {
  private val DefaultListLimit: BattleResultListLimit =
    BattleResultListLimit(25)

  given userIdDecoder: Decoder[UserId] =
    Decoder.decodeString.emap(value => nonEmpty(value).map(UserId.apply).toRight("Login is required."))

  given battleIdDecoder: Decoder[BattleId] =
    Decoder.decodeString.emap(value =>
      nonEmpty(value)
        .map(BattleId.apply)
        .toRight(BattleResultRecordRequestDecodeError.message(BattleResultRecordRequestDecodeError.InvalidBattleId))
    )

  given battleIdOptionDecoder: Decoder[Option[BattleId]] =
    optionalTextDecoder.map(_.map(BattleId.apply))

  given playerHandleDecoder: Decoder[PlayerHandle] =
    Decoder.decodeString.emap(value =>
      nonEmpty(value)
        .map(PlayerHandle.apply)
        .toRight(BattleResultRecordRequestDecodeError.message(BattleResultRecordRequestDecodeError.InvalidHandle))
    )

  given playerHandleOptionDecoder: Decoder[Option[PlayerHandle]] =
    optionalTextDecoder.map(_.flatMap(PlayerHandle.forLookup))

  given displayNameOptionDecoder: Decoder[Option[DisplayName]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(DisplayName.apply))

  given battleResultListLimitDecoder: Decoder[BattleResultListLimit] =
    optionalIntDecoder.map(_.map(BattleResultListLimit.apply).getOrElse(DefaultListLimit))

  given epochMillisDecoder: Decoder[EpochMillis] =
    Decoder.decodeOption(Decoder.decodeLong).map(value => EpochMillis(math.max(0L, value.getOrElse(0L))))

  given finishedAtLabelDecoder: Decoder[BattleResultFinishedAtLabel] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattleResultFinishedAtLabel.fromWire(value.getOrElse("")))

  given durationMillisDecoder: Decoder[DurationMillis] =
    Decoder.decodeOption(Decoder.decodeLong).map(value => DurationMillis(math.max(0L, value.getOrElse(0L))))

  given scoreDecoder: Decoder[Score] =
    Decoder.decodeOption(Decoder.decodeInt)
      .withErrorMessage(BattleResultRecordRequestDecodeError.message(BattleResultRecordRequestDecodeError.InvalidField("score")))
      .map(value => Score(math.max(0, value.getOrElse(0))))

  given placementOptionDecoder: Decoder[Option[BattlePlacement]] =
    Decoder.decodeOption(Decoder.decodeInt).map(_.flatMap(BattlePlacement.fromWire))

  given survivalOutcomeDecoder: Decoder[BattleSurvivalOutcome] =
    Decoder.decodeOption(Decoder.decodeBoolean).map(value => BattleSurvivalOutcome.fromAliveAtEnd(value.getOrElse(false)))

  given ratingDecoder: Decoder[Rating] =
    Decoder.decodeOption(Decoder.decodeInt).map(value => Rating(value.getOrElse(0)))

  given ratingDeltaDecoder: Decoder[RatingDelta] =
    Decoder.decodeOption(Decoder.decodeInt).map(value => RatingDelta(value.getOrElse(0)))

  given resultLabelDecoder: Decoder[BattleResultLabel] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattleResultLabel.fromWire(value.getOrElse("")))

  given modeLabelDecoder: Decoder[BattleModeLabel] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattleModeLabel.fromWire(value.getOrElse("")))

  given mapLabelDecoder: Decoder[BattleMapLabel] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattleMapLabel.fromWire(value.getOrElse("")))

  given highlightLineDecoder: Decoder[BattleHighlightLine] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattleHighlightLine.fromWire(value.getOrElse("")))

  given playersLineDecoder: Decoder[BattlePlayersLine] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattlePlayersLine.fromWire(value.getOrElse("")))

  given timelineHintDecoder: Decoder[BattleTimelineHint] =
    Decoder.decodeOption(Decoder.decodeString).map(value => BattleTimelineHint.fromWire(value.getOrElse("")))

  given currentLoadoutDecoder: Decoder[Option[BattleResultLoadoutLabel]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(BattleResultLoadoutLabel.fromWire))

  private val optionalTextDecoder: Decoder[Option[String]] =
    Decoder.decodeOption(Decoder.decodeString).or(Decoder.const(None)).map(_.flatMap(nonEmpty))

  private val optionalIntDecoder: Decoder[Option[Int]] =
    Decoder.decodeOption(Decoder.decodeInt)
      .or(Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(_.trim.toIntOption)))
      .or(Decoder.const(None))

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
