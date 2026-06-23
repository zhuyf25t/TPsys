package services.replay.api

import io.circe.Decoder

import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Score}
import services.battle.microservices.results.objects.result.BattlePlacement
import services.battle.objects.{BattleId, DurationMillis, EpochMillis}
import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.objects.{ReplayFrameCount, ReplayId, ReplayPlaybackAvailability}

private[api] object ReplayAPIMessageDecoding {
  given replayIdOptionDecoder: Decoder[Option[ReplayId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(ReplayApiCodec.replayIdFromWire))

  given battleIdOptionDecoder: Decoder[Option[BattleId]] =
    Decoder.decodeOption(Decoder.decodeString).map(ReplayRecordBattleIdInput.fromWire)

  given selectedHandleOptionDecoder: Decoder[Option[PlayerHandle]] =
    Decoder.decodeOption(Decoder.decodeString).map(ReplaySelectedHandleInput.fromWire)

  given replayRecordHandleInputDecoder: Decoder[ReplayRecordHandleInput] =
    Decoder.decodeOption(Decoder.decodeString).map(ReplayRecordHandleInput.fromWire)

  given displayNameOptionDecoder: Decoder[Option[DisplayName]] =
    Decoder.decodeOption(Decoder.decodeString).map(ReplayRecordDisplayNameInput.fromWire)

  given epochMillisDecoder: Decoder[EpochMillis] =
    Decoder.decodeOption(Decoder.decodeLong).map(ReplayRecordTimestampInput.fromWire)

  given replayRecordTextInputDecoder: Decoder[ReplayRecordTextInput] =
    Decoder.decodeOption(Decoder.decodeString).map(ReplayRecordTextInput.fromWire)

  given scoreDecoder: Decoder[Score] =
    Decoder.decodeOption(Decoder.decodeInt).map(ReplayRecordScoreInput.fromWire)

  given placementOptionDecoder: Decoder[Option[BattlePlacement]] =
    Decoder.decodeOption(Decoder.decodeInt).map(_.flatMap(BattlePlacement.fromWire))

  given durationMillisDecoder: Decoder[DurationMillis] =
    Decoder.decodeOption(Decoder.decodeLong).map(ReplayRecordDurationInput.fromWire)

  given survivalOutcomeDecoder: Decoder[BattleSurvivalOutcome] =
    Decoder.decodeOption(Decoder.decodeBoolean).map(ReplayRecordSurvivalInput.fromWire)

  given replayRecordOptionalTextInputDecoder: Decoder[ReplayRecordOptionalTextInput] =
    Decoder.decodeOption(Decoder.decodeString).map(ReplayRecordOptionalTextInput.fromWire)

  given replayFrameCountDecoder: Decoder[ReplayFrameCount] =
    Decoder.decodeOption(Decoder.decodeInt).map(ReplayRecordFrameCountInput.fromWire)

  given replayPlaybackAvailabilityDecoder: Decoder[ReplayPlaybackAvailability] =
    Decoder.decodeOption(Decoder.decodeBoolean).map(ReplayRecordPlaybackInput.fromWire)

  given replayRecordFramesJsonOptionDecoder: Decoder[Option[ReplayRecordFramesJsonInput]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.map(ReplayRecordFramesJsonInput.fromWire))

  given replayRecordFramesPayloadOptionDecoder: Decoder[Option[ReplayRecordFramesPayloadInput]] =
    Decoder.decodeOption(Decoder.decodeJson).map(_.map(json => ReplayRecordFramesPayloadInput(json.noSpaces)))

  given replayListLimitInputDecoder: Decoder[ReplayListLimitInput] =
    Decoder.decodeInt.map(value => ReplayListLimitInput.fromWire(Some(value)))

  given replayCommentAuthorInputDecoder: Decoder[ReplayCommentAuthorInput] =
    Decoder.decodeString.map(value => ReplayCommentAuthorInput.fromWire(Some(value)))

  given replayCommentBodyInputDecoder: Decoder[ReplayCommentBodyInput] =
    Decoder.decodeString.map(value => ReplayCommentBodyInput.fromWire(Some(value)))
}
