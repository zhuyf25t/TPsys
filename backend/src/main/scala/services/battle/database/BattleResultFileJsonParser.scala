package services.battle.database

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse as parseJson

import services.battle.objects.{
  BattleHighlightLine,
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultLabel,
  BattleResultRecord,
  BattleSurvivalOutcome,
  BattleTimelineHint,
  DurationMillis,
  EpochMillis,
  Rating,
  RatingDelta,
  Score
}
import services.identity.objects.{DisplayName, PlayerHandle}

private[database] object BattleResultFileJsonParser {
  def parseRecords(raw: String): Vector[BattleResultRecord] =
    parseJson(raw)
      .toOption
      .flatMap(_.as[BattleResultFileJsonPayload].toOption)
      .map(_.toDomain)
      .getOrElse(Vector.empty)
}

private[database] final case class BattleResultFileJsonPayload(
  schema: String,
  results: Vector[BattleResultFileRecordJson]
) {
  def toDomain: Vector[BattleResultRecord] =
    results.map(_.toDomain)
}

private[database] object BattleResultFileJsonPayload {
  private val Schema = "slay-demo.battle-results.v1"

  given Encoder[BattleResultFileJsonPayload] = deriveEncoder

  given Decoder[BattleResultFileJsonPayload] = Decoder.instance { cursor =>
    for
      schema <- cursor.get[Option[String]]("schema").orElse(Right(Some(Schema)))
      results <- decodeResults(cursor)
    yield BattleResultFileJsonPayload(schema = schema.getOrElse(Schema), results = results)
  }

  def fromDomain(records: Vector[BattleResultRecord]): BattleResultFileJsonPayload =
    BattleResultFileJsonPayload(
      schema = Schema,
      results = records.map(BattleResultFileRecordJson.fromDomain)
    )

  private def decodeResults(cursor: HCursor): Decoder.Result[Vector[BattleResultFileRecordJson]] =
    cursor
      .get[Option[Vector[Json]]]("results")
      .map(_.getOrElse(Vector.empty).flatMap(_.as[BattleResultFileRecordJson].toOption))
      .orElse(Right(Vector.empty))
}

private[database] final case class BattleResultFileRecordJson(
  battleId: String,
  resultId: Option[String],
  handle: String,
  displayName: String,
  finishedAt: Long,
  finishedAtLabel: String,
  durationMs: Long,
  score: Int,
  placement: Option[Int],
  aliveAtEnd: Boolean,
  ratingBefore: Int,
  ratingDelta: Int,
  ratingAfter: Int,
  resultLabel: String,
  modeLabel: String,
  mapLabel: String,
  highlightLine: String,
  playersLine: String,
  timelineHint: String,
  currentLoadout: Option[String]
) {
  def toDomain: BattleResultRecord =
    BattleResultRecord(
      battleId = BattleId(battleId),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      finishedAt = EpochMillis(finishedAt),
      finishedAtLabel = finishedAtLabel,
      durationMs = DurationMillis(durationMs),
      score = Score(score),
      placement = placement.flatMap(BattlePlacement.fromWire),
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
      currentLoadout = currentLoadout.flatMap(nonEmptyText)
    )

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

private[database] object BattleResultFileRecordJson {
  given Encoder[BattleResultFileRecordJson] = deriveEncoder
  given Decoder[BattleResultFileRecordJson] = deriveDecoder

  def fromDomain(record: BattleResultRecord): BattleResultFileRecordJson =
    BattleResultFileRecordJson(
      battleId = record.battleId.value,
      resultId = Some(record.resultId.value),
      handle = record.handle.value,
      displayName = record.displayName.value,
      finishedAt = record.finishedAt.value,
      finishedAtLabel = record.finishedAtLabel,
      durationMs = record.durationMs.value,
      score = record.score.value,
      placement = record.placement.map(_.value),
      aliveAtEnd = record.aliveAtEnd,
      ratingBefore = record.ratingBefore.value,
      ratingDelta = record.ratingDelta.value,
      ratingAfter = record.ratingAfter.value,
      resultLabel = record.resultLabel.value,
      modeLabel = record.modeLabel.value,
      mapLabel = record.mapLabel.value,
      highlightLine = record.highlightLine.value,
      playersLine = record.playersLine.value,
      timelineHint = record.timelineHint.value,
      currentLoadout = record.currentLoadout
    )
}
