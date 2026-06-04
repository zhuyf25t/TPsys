package services.battle.microservices.projections.services

import cats.effect.IO

import services.battle.objects.core.BattleAggregateState
import services.battle.microservices.results.objects.result.BattleResultRecord
import services.replay.objects.{ReplayFrameCount, ReplayFramesJson, ReplayId, ReplayPlaybackAvailability, ReplayRecord, ReplaySettlementRecord}

private[battle] object BattleFinishProjectionReplayRules {
  def replayOwnerSettlement(
    state: BattleAggregateState,
    settlements: BattleSettlements
  ): IO[BattleSettlement] =
    state.winnerPlayerId match {
      case Some(winnerPlayerId) =>
        settlements.find(_.player.exists(_.playerId == winnerPlayerId)).map(_.getOrElse(settlements.first))
      case None =>
        IO.pure(settlements.first)
    }

  def replayRecord(state: BattleAggregateState, settlements: BattleSettlements): IO[ReplayRecord] =
    for
      ownerSettlement <- replayOwnerSettlement(state, settlements)
      result = ownerSettlement.result
      durationMs <- BattleFinishProjectionTimeRules.projectedDuration(state)
      replayFrames <- BattleReplayFramesJsonRenderer.render(state, durationMs)
      title <- BattleFinishProjectionLabelRules.replayTitle(result)
      replayResultLabel <- BattleFinishProjectionLabelRules.replayResultLabel(state)
      settlementValues <- settlements.toVector
      replaySettlements <- settlementValues.foldLeft(IO.pure(Vector.empty[ReplaySettlementRecord])) {
        case (previous, settlement) =>
          for
            values <- previous
            value <- replaySettlement(settlement.result)
          yield values :+ value
      }
    yield ReplayRecord(
      replayId = ReplayId(state.battleId.value),
      battleId = state.battleId,
      handle = result.handle,
      displayName = result.displayName,
      finishedAt = result.finishedAt,
      finishedAtLabel = result.finishedAtLabel,
      title = title,
      modeLabel = result.modeLabel.value,
      resultLabel = replayResultLabel,
      mapLabel = result.mapLabel.value,
      highlightLine = result.highlightLine.value,
      coverLabel = BattleFinishProjectionLabelRules.CoverLabel,
      playersLine = result.playersLine.value,
      timelineHint = result.timelineHint.value,
      score = result.score,
      placement = result.placement,
      ratingBefore = Some(result.ratingBefore),
      ratingDelta = Some(result.ratingDelta),
      ratingAfter = Some(result.ratingAfter),
      durationMs = result.durationMs,
      survivalOutcome = result.survivalOutcome,
      thumbnailDataUrl = None,
      currentLoadout = result.currentLoadout,
      frameCount = ReplayFrameCount.fromWire(replayFrames.frameCount),
      playbackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(replayFrames.frameCount >= 2),
      framesJson = ReplayFramesJson.fromNormalized(replayFrames.json),
      settlements = replaySettlements
    )

  def replaySettlement(result: BattleResultRecord): IO[ReplaySettlementRecord] =
    IO.pure(
      ReplaySettlementRecord(
        handle = result.handle,
        displayName = result.displayName,
        resultLabel = result.resultLabel.value,
        highlightLine = result.highlightLine.value,
        score = result.score,
        placement = result.placement,
        ratingBefore = Some(result.ratingBefore),
        ratingDelta = Some(result.ratingDelta),
        ratingAfter = Some(result.ratingAfter),
        survivalOutcome = result.survivalOutcome,
        currentLoadout = result.currentLoadout
      )
    )
}
