package services.battle.microservices.results.api.results

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.results.objects.result.{BattleResultList, BattleResultRecord}

private[api] object BattleResultResponseMapping {
  def fromRecord(value: BattleResultRecord): IO[BattleResultRecordResponse] =
    IO.pure(
      BattleResultRecordResponse(
        resultId = value.resultId.value,
        battleId = value.battleId.value,
        handle = value.handle.value,
        displayName = value.displayName.value,
        finishedAt = value.finishedAt.value,
        finishedAtLabel = value.finishedAtLabel.value,
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
        currentLoadout = value.currentLoadout.map(_.value)
      )
    )

  def fromList(value: BattleResultList): IO[BattleResultListResponse] =
    fromRecords(value.results)

  def fromRecords(values: Vector[BattleResultRecord]): IO[BattleResultListResponse] =
    values.traverse(fromRecord).map(BattleResultListResponse.apply)
}
