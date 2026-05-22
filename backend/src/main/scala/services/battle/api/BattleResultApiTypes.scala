package services.battle.api

import io.circe.Encoder

import services.battle.objects.BattleResultRecord

final case class BattleResultRecordResponse(
  resultId: String,
  battleId: String,
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
)

object BattleResultRecordResponse {
  given Encoder[BattleResultRecordResponse] =
    Encoder.forProduct20(
      "resultId",
      "battleId",
      "handle",
      "displayName",
      "finishedAt",
      "finishedAtLabel",
      "durationMs",
      "score",
      "placement",
      "aliveAtEnd",
      "ratingBefore",
      "ratingDelta",
      "ratingAfter",
      "resultLabel",
      "modeLabel",
      "mapLabel",
      "highlightLine",
      "playersLine",
      "timelineHint",
      "currentLoadout"
    )(value =>
      (
        value.resultId,
        value.battleId,
        value.handle,
        value.displayName,
        value.finishedAt,
        value.finishedAtLabel,
        value.durationMs,
        value.score,
        value.placement,
        value.aliveAtEnd,
        value.ratingBefore,
        value.ratingDelta,
        value.ratingAfter,
        value.resultLabel,
        value.modeLabel,
        value.mapLabel,
        value.highlightLine,
        value.playersLine,
        value.timelineHint,
        value.currentLoadout
      )
    )

  def fromRecord(record: BattleResultRecord): BattleResultRecordResponse =
    BattleResultRecordResponse(
      resultId = record.resultId.value,
      battleId = record.battleId.value,
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

final case class BattleResultListResponse(results: Vector[BattleResultRecordResponse])

object BattleResultListResponse {
  given Encoder[BattleResultListResponse] =
    Encoder.forProduct1("results")(_.results)

  val Empty: BattleResultListResponse =
    BattleResultListResponse(Vector.empty)

  def fromRecords(records: Vector[BattleResultRecord]): BattleResultListResponse =
    BattleResultListResponse(records.map(BattleResultRecordResponse.fromRecord))
}
