package services.battle.objects.apiTypes.results

import io.circe.Encoder
import services.battle.objects.result.{BattleResultList, BattleResultRecord}

object BattleResultRecordResponse {
  given Encoder[BattleResultRecord] =
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
        value.resultId.value,
        value.battleId.value,
        value.handle.value,
        value.displayName.value,
        value.finishedAt.value,
        value.finishedAtLabel,
        value.durationMs.value,
        value.score.value,
        value.placement.map(_.value),
        value.aliveAtEnd,
        value.ratingBefore.value,
        value.ratingDelta.value,
        value.ratingAfter.value,
        value.resultLabel.value,
        value.modeLabel.value,
        value.mapLabel.value,
        value.highlightLine.value,
        value.playersLine.value,
        value.timelineHint.value,
        value.currentLoadout
      )
    )
}

object BattleResultListResponse {
  import BattleResultRecordResponse.given

  given Encoder[BattleResultList] =
    Encoder.forProduct1("results")(_.results)
}
