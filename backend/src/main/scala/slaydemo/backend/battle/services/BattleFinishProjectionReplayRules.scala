package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.replay.objects.{ReplayFrameCount, ReplayFramesJson, ReplayId, ReplayPlaybackAvailability, ReplayRecord, ReplaySettlementRecord}

private[services] object BattleFinishProjectionReplayRules {
  def replayOwnerSettlement(
    state: BattleAggregateState,
    settlements: Vector[BattleSettlement]
  ): BattleSettlement =
    state.winnerPlayerId
      .flatMap(winnerPlayerId => settlements.find(_.player.exists(_.playerId == winnerPlayerId)))
      .getOrElse(settlements.head)

  def replayRecord(state: BattleAggregateState, settlements: Vector[BattleSettlement]): ReplayRecord = {
    val result = replayOwnerSettlement(state, settlements).result
    val replayFrames = BattleReplayFramesJsonRenderer.render(
      state,
      BattleFinishProjectionTimeRules.projectedDuration(state)
    )
    ReplayRecord(
      replayId = ReplayId(state.battleId.value),
      battleId = state.battleId,
      handle = result.handle,
      displayName = result.displayName,
      finishedAt = result.finishedAt,
      finishedAtLabel = result.finishedAtLabel,
      title = BattleFinishProjectionLabelRules.replayTitle(result),
      modeLabel = result.modeLabel.value,
      resultLabel = BattleFinishProjectionLabelRules.replayResultLabel(state),
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
      settlements = settlements.map(settlement => replaySettlement(settlement.result))
    )
  }

  def replaySettlement(result: BattleResultRecord): ReplaySettlementRecord =
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
}
