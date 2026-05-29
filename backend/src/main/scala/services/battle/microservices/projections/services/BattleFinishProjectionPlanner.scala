package services.battle.microservices.projections.services

import services.battle.objects.core.{
  BattleAggregateState,
  DurationMillis,
  EpochMillis,
  PlayerId
}
import services.battle.microservices.actors.objects.player.{BattlePlayerState, BattleSurvivalOutcome, Rating, Score}
import services.battle.microservices.results.objects.result.{BattlePlacement, BattlePlayersLine, BattleResultRecord, RatingDelta}
import services.identity.objects.PlayerHandle
import services.replay.objects.ReplayRecord

private[battle] final case class BattleSettlement(
  player: Option[BattlePlayerState],
  result: BattleResultRecord
)

private[battle] final case class BattleSettlements(
  first: BattleSettlement,
  rest: Vector[BattleSettlement]
) {
  def toVector: Vector[BattleSettlement] =
    first +: rest

  def map[A](f: BattleSettlement => A): Vector[A] =
    toVector.map(f)

  def foreach[U](f: BattleSettlement => U): Unit =
    toVector.foreach(f)

  def find(f: BattleSettlement => Boolean): Option[BattleSettlement] =
    toVector.find(f)
}

private[battle] object BattleSettlements {
  def fromVectorOrFallback(
    values: Vector[BattleSettlement],
    fallback: => BattleSettlement
  ): BattleSettlements =
    values.headOption match {
      case Some(first) =>
        BattleSettlements(first, values.drop(1))
      case None =>
        BattleSettlements(fallback, Vector.empty)
    }
}

private[battle] final case class BattleFinishProjectionPlan(
  settlements: BattleSettlements,
  replay: Option[ReplayRecord]
)

private[battle] final case class BattleSettlementBuildContext(
  orderedPlayers: Vector[BattlePlayerState],
  placementsByPlayerId: Map[PlayerId, BattlePlacement],
  playersLine: String,
  finishedAt: EpochMillis,
  durationMs: DurationMillis,
  playerCount: Int
)

private[battle] final case class BattlePreviousRatings private (
  ratingsByHandleKey: Map[String, Rating]
) {
  /** 中文名：积分before（ratingBefore）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def ratingBefore(handle: PlayerHandle): Rating =
    ratingsByHandleKey.getOrElse(handle.key, BattleSettlementScoringRules.DefaultRating)
}

private[battle] object BattlePreviousRatings {
  val empty: BattlePreviousRatings =
    BattlePreviousRatings(Map.empty)

  /** 中文名：从ratings（fromRatings）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def fromRatings(ratings: Iterable[(PlayerHandle, Rating)]): BattlePreviousRatings =
    BattlePreviousRatings(
      ratings.map { case (handle, rating) => handle.key -> rating }.toMap
    )
}

private[battle] object BattleFinishProjectionPlanner {
  /** 中文名：构建（build）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def build(
    state: BattleAggregateState,
    previousRatings: BattlePreviousRatings
  ): BattleFinishProjectionPlan = {
    val settlements = buildSettlements(state, previousRatings)
    BattleFinishProjectionPlan(
      settlements = settlements,
      replay = Some(BattleFinishProjectionReplayRules.replayRecord(state, settlements))
    )
  }

  /** 中文名：humanplayersbyplacement（humanPlayersByPlacement）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def humanPlayersByPlacement(state: BattleAggregateState): Vector[BattlePlayerState] =
    BattleFinishProjectionPlayerRules
      .playersByPlacement(state.players)
      .filter(BattleFinishProjectionPlayerRules.isPlayableHumanPlayer)

  private def buildSettlements(
    state: BattleAggregateState,
    previousRatings: BattlePreviousRatings
  ): BattleSettlements = {
    val orderedPlayers = BattleFinishProjectionPlayerRules.playersByPlacement(state.players)
    val context = settlementBuildContext(state, orderedPlayers)

    val playableHumanSettlements = orderedPlayers
      .filter(BattleFinishProjectionPlayerRules.isPlayableHumanPlayer)
      .map(player => playableSettlementFor(state, context, previousRatings, player))

    BattleSettlements.fromVectorOrFallback(
      playableHumanSettlements,
      BattleSettlement(
        player = None,
        result = serverResult(state, context.finishedAt, context.durationMs, context.playersLine, previousRatings)
      )
    )
  }

  private def settlementBuildContext(
    state: BattleAggregateState,
    orderedPlayers: Vector[BattlePlayerState]
  ): BattleSettlementBuildContext =
    BattleSettlementBuildContext(
      orderedPlayers = orderedPlayers,
      placementsByPlayerId = orderedPlayers.zipWithIndex.map { case (player, index) =>
        player.playerId -> BattlePlacement.unsafe(index + 1)
      }.toMap,
      playersLine = BattleFinishProjectionLabelRules.playersLine(state.players).value,
      finishedAt = BattleFinishProjectionTimeRules.projectedFinishedAt(state),
      durationMs = BattleFinishProjectionTimeRules.projectedDuration(state),
      playerCount = orderedPlayers.length
    )

  private def playableSettlementFor(
    state: BattleAggregateState,
    context: BattleSettlementBuildContext,
    previousRatings: BattlePreviousRatings,
    player: BattlePlayerState
  ): BattleSettlement = {
    val placement = context.placementsByPlayerId.getOrElse(player.playerId, BattlePlacement.unsafe(context.orderedPlayers.length))
    val score = BattleSettlementScoringRules.placementScore(Some(placement), context.playerCount)
    val ratingBefore = previousRatings.ratingBefore(player.handle)
    val survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(player.alive)
    val ratingDelta = BattleSettlementScoringRules.ratingDelta(score, Some(placement), survivalOutcome)
    val ratingAfter = Rating(ratingBefore.value + ratingDelta.value)
    val result = BattleResultRecord(
      battleId = state.battleId,
      handle = player.handle,
      displayName = player.displayName,
      finishedAt = context.finishedAt,
      finishedAtLabel = BattleFinishProjectionLabelRules.finishedAtLabel(context.finishedAt),
      durationMs = context.durationMs,
      score = Score(score),
      placement = Some(placement),
      survivalOutcome = survivalOutcome,
      ratingBefore = ratingBefore,
      ratingDelta = ratingDelta,
      ratingAfter = ratingAfter,
      resultLabel = BattleFinishProjectionLabelRules.resultLabel(player, placement),
      modeLabel = BattleFinishProjectionLabelRules.modeLabel,
      mapLabel = BattleFinishProjectionLabelRules.mapLabel,
      highlightLine = BattleFinishProjectionLabelRules.highlightLine(player, placement, score),
      playersLine = BattlePlayersLine.fromWire(context.playersLine),
      timelineHint = BattleFinishProjectionLabelRules.timelineHint(player),
      currentLoadout = None
    )

    BattleSettlement(player = Some(player), result = result)
  }

  private def serverResult(
    state: BattleAggregateState,
    finishedAt: EpochMillis,
    durationMs: DurationMillis,
    playersLine: String,
    previousRatings: BattlePreviousRatings
  ): BattleResultRecord = {
    val handle = PlayerHandle("server")
    val ratingBefore = previousRatings.ratingBefore(handle)
    BattleResultRecord(
      battleId = state.battleId,
      handle = handle,
      displayName = BattleFinishProjectionLabelRules.serverDisplayName,
      finishedAt = finishedAt,
      finishedAtLabel = BattleFinishProjectionLabelRules.finishedAtLabel(finishedAt),
      durationMs = durationMs,
      score = Score(0),
      placement = None,
      survivalOutcome = BattleSurvivalOutcome.Eliminated,
      ratingBefore = ratingBefore,
      ratingDelta = RatingDelta(0),
      ratingAfter = ratingBefore,
      resultLabel = BattleFinishProjectionLabelRules.serverResultLabel,
      modeLabel = BattleFinishProjectionLabelRules.modeLabel,
      mapLabel = BattleFinishProjectionLabelRules.mapLabel,
      highlightLine = BattleFinishProjectionLabelRules.serverHighlightLine(state.battleId),
      playersLine = BattlePlayersLine.fromWire(playersLine),
      timelineHint = BattleFinishProjectionLabelRules.serverTimelineHint,
      currentLoadout = None
    )
  }

}
