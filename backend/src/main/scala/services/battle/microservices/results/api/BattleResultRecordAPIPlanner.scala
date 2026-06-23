package services.battle.microservices.results.api

import cats.effect.IO

import java.sql.Connection

import services.battle.microservices.results.api.results.{BattleResultRecordResponse, BattleResultResponseMapping}
import services.battle.microservices.results.objects.result.BattleResultRecordCommand
import services.battle.microservices.results.services.BattleResultService
import services.identity.objects.DisplayName

object BattleResultRecordAPIPlanner {
  def plan(connection: Connection, message: BattleResultRecordAPIMessage): IO[BattleResultRecordResponse] =
    for
      saved <- BattleResultService
        .record(connection, toCommand(message))
        .flatMap(result => IO.fromEither(result.left.map(BattleResultAPIMessageErrors.recordValidation)))
      response <- BattleResultResponseMapping.fromRecord(saved)
    yield response

  private def toCommand(message: BattleResultRecordAPIMessage): BattleResultRecordCommand =
    BattleResultRecordCommand(
      battleId = message.battleId,
      handle = message.handle,
      displayName = message.displayName.getOrElse(DisplayName(message.handle.value)),
      finishedAt = message.finishedAt,
      finishedAtLabel = message.finishedAtLabel,
      durationMs = message.durationMs,
      score = message.score,
      placement = message.placement,
      survivalOutcome = message.survivalOutcome,
      ratingBefore = message.ratingBefore,
      ratingDelta = message.ratingDelta,
      ratingAfter = message.ratingAfter,
      resultLabel = message.resultLabel,
      modeLabel = message.modeLabel,
      mapLabel = message.mapLabel,
      highlightLine = message.highlightLine,
      playersLine = message.playersLine,
      timelineHint = message.timelineHint,
      currentLoadout = message.currentLoadout
    )
}
