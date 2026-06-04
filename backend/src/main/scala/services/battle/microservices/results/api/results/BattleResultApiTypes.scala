package services.battle.microservices.results.api.results

import io.circe.Encoder

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

  given Encoder[BattleResultListResponse] =
    Encoder.forProduct1("results")(_.results)
}
