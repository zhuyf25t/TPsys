package services.battle.microservices.projections.services

import services.battle.objects.core.BattleAggregateState
import services.battle.microservices.results.objects.result.BattleResultRecord
import services.replay.objects.{ReplayFrameCount, ReplayFramesJson, ReplayId, ReplayPlaybackAvailability, ReplayRecord, ReplaySettlementRecord}

private[battle] object BattleFinishProjectionReplayRules {
  /** 中文名：回放ownersettlement（replayOwnerSettlement）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def replayOwnerSettlement(
    state: BattleAggregateState,
    settlements: BattleSettlements
  ): BattleSettlement =
    state.winnerPlayerId
      .flatMap(winnerPlayerId => settlements.find(_.player.exists(_.playerId == winnerPlayerId)))
      .getOrElse(settlements.first)

  /** 中文名：回放记录（replayRecord）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def replayRecord(state: BattleAggregateState, settlements: BattleSettlements): ReplayRecord = {
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

  /** 中文名：回放settlement（replaySettlement）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
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
