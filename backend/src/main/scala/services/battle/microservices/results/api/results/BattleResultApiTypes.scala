package services.battle.microservices.results.api.results

import io.circe.Encoder
import services.battle.microservices.results.objects.result.{BattleResultList, BattleResultRecord}

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
  def fromRecord(value: BattleResultRecord): BattleResultRecordResponse =
    BattleResultRecordResponse(
      resultId = value.resultId.value,
      battleId = value.battleId.value,
      handle = value.handle.value,
      displayName = value.displayName.value,
      finishedAt = value.finishedAt.value,
      finishedAtLabel = value.finishedAtLabel,
      durationMs = value.durationMs.value,
      score = value.score.value,
      placement = value.placement.map(_.value),
      aliveAtEnd = value.aliveAtEnd,
      ratingBefore = value.ratingBefore.value,
      ratingDelta = value.ratingDelta.value,
      ratingAfter = value.ratingAfter.value,
      resultLabel = value.resultLabel.value,
      modeLabel = value.modeLabel.value,
      mapLabel = value.mapLabel.value,
      highlightLine = value.highlightLine.value,
      playersLine = value.playersLine.value,
      timelineHint = value.timelineHint.value,
      currentLoadout = value.currentLoadout
    )

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
    )(response =>
      (
        response.resultId,
        response.battleId,
        response.handle,
        response.displayName,
        response.finishedAt,
        response.finishedAtLabel,
        response.durationMs,
        response.score,
        response.placement,
        response.aliveAtEnd,
        response.ratingBefore,
        response.ratingDelta,
        response.ratingAfter,
        response.resultLabel,
        response.modeLabel,
        response.mapLabel,
        response.highlightLine,
        response.playersLine,
        response.timelineHint,
        response.currentLoadout
      )
    )
}

final case class BattleResultListResponse(results: Vector[BattleResultRecordResponse])

object BattleResultListResponse {
  import BattleResultRecordResponse.given

  def fromList(value: BattleResultList): BattleResultListResponse =
    BattleResultListResponse(value.results.map(BattleResultRecordResponse.fromRecord))

  def fromRecords(value: Vector[BattleResultRecord]): BattleResultListResponse =
    BattleResultListResponse(value.map(BattleResultRecordResponse.fromRecord))

  given Encoder[BattleResultListResponse] =
    Encoder.forProduct1("results")(_.results)
}
