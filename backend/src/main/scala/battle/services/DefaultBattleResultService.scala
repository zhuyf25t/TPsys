package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.BattleResultSubmissionRequest
import slaydemo.backend.battle.database.BattleResultRepository
import slaydemo.backend.battle.objects.BattleResultRecord

final class DefaultBattleResultService(repository: BattleResultRepository) extends BattleResultService {
  override def record(request: BattleResultSubmissionRequest): Either[String, BattleResultRecord] = {
    val handle = request.handle.value.trim
    val battleId = request.battleId.value.trim

    if (handle.isEmpty) {
      Left("invalid_handle")
    } else if (battleId.isEmpty) {
      Left("invalid_battle_id")
    } else {
      val record = BattleResultRecord(
        battleId = request.battleId,
        handle = request.handle,
        displayName = request.displayName.trim,
        finishedAt = request.finishedAt,
        finishedAtLabel = request.finishedAtLabel.trim,
        durationMs = request.durationMs,
        score = request.score,
        placement = request.placement,
        aliveAtEnd = request.aliveAtEnd,
        ratingBefore = request.ratingBefore,
        ratingDelta = request.ratingDelta,
        ratingAfter = request.ratingAfter,
        resultLabel = request.resultLabel.trim,
        modeLabel = request.modeLabel.trim,
        mapLabel = request.mapLabel.trim,
        highlightLine = request.highlightLine.trim,
        playersLine = request.playersLine.trim,
        timelineHint = request.timelineHint.trim,
        currentLoadout = request.currentLoadout.map(_.trim).filter(_.nonEmpty)
      )

      Right(repository.save(record))
    }
  }

  override def list(handle: Option[String], limit: Int): Seq[BattleResultRecord] = {
    val bounded = limit.max(1).min(50)
    handle.map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => repository.listByHandle(value, bounded)
      case None        => repository.list(bounded)
    }
  }
}

