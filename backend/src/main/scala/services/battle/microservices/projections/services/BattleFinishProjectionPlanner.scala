package services.battle.microservices.projections.services

import cats.effect.IO

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
  def toVector: IO[Vector[BattleSettlement]] =
    IO.pure(first +: rest)

  def map[A](f: BattleSettlement => A): IO[Vector[A]] =
    toVector.map(_.map(f))

  def foreach[U](f: BattleSettlement => U): IO[Unit] =
    toVector.map(_.foreach(f))

  def find(f: BattleSettlement => Boolean): IO[Option[BattleSettlement]] =
    toVector.map(_.find(f))
}

private[battle] object BattleSettlements {
  def fromVectorOrFallback(
    values: Vector[BattleSettlement],
    fallback: => BattleSettlement
  ): IO[BattleSettlements] =
    IO.pure(values.headOption match {
      case Some(first) =>
        BattleSettlements(first, values.drop(1))
      case None =>
        BattleSettlements(fallback, Vector.empty)
    })
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
  def ratingBefore(handle: PlayerHandle): IO[Rating] =
    IO.pure(ratingsByHandleKey.getOrElse(handle.key, BattleSettlementScoringRules.DefaultRating))
}

private[battle] object BattlePreviousRatings {
  val empty: BattlePreviousRatings =
    BattlePreviousRatings(Map.empty)

  def fromRatings(ratings: Iterable[(PlayerHandle, Rating)]): IO[BattlePreviousRatings] =
    IO.pure(BattlePreviousRatings(
      ratings.map { case (handle, rating) => handle.key -> rating }.toMap
    ))
}

private[battle] object BattleFinishProjectionPlanner {
  def build(
    state: BattleAggregateState,
    previousRatings: BattlePreviousRatings
  ): IO[BattleFinishProjectionPlan] =
    for
      settlements <- buildSettlements(state, previousRatings)
      replay <- BattleFinishProjectionReplayRules.replayRecord(state, settlements)
    yield BattleFinishProjectionPlan(
      settlements = settlements,
      replay = Some(replay)
    )

  def humanPlayersByPlacement(state: BattleAggregateState): IO[Vector[BattlePlayerState]] =
    for
      orderedPlayers <- BattleFinishProjectionPlayerRules.playersByPlacement(state.players)
      playablePlayers <- orderedPlayers.foldLeft(IO.pure(Vector.empty[BattlePlayerState])) { case (previous, player) =>
        for
          players <- previous
          playable <- BattleFinishProjectionPlayerRules.isPlayableHumanPlayer(player)
        yield if playable then players :+ player else players
      }
    yield playablePlayers

  private def buildSettlements(
    state: BattleAggregateState,
    previousRatings: BattlePreviousRatings
  ): IO[BattleSettlements] =
    for
      orderedPlayers <- BattleFinishProjectionPlayerRules.playersByPlacement(state.players)
      context <- settlementBuildContext(state, orderedPlayers)
      playableHumanSettlements <- orderedPlayers.foldLeft(IO.pure(Vector.empty[BattleSettlement])) {
        case (previous, player) =>
          for
            settlements <- previous
            playable <- BattleFinishProjectionPlayerRules.isPlayableHumanPlayer(player)
            next <-
              if playable then playableSettlementFor(state, context, previousRatings, player).map(settlements :+ _)
              else IO.pure(settlements)
          yield next
      }
      settlements <- playableHumanSettlements.headOption match {
        case Some(first) =>
          IO.pure(BattleSettlements(first, playableHumanSettlements.drop(1)))
        case None =>
          serverResult(state, context.finishedAt, context.durationMs, context.playersLine, previousRatings)
            .map(result => BattleSettlements(BattleSettlement(player = None, result = result), Vector.empty))
      }
    yield settlements

  private def settlementBuildContext(
    state: BattleAggregateState,
    orderedPlayers: Vector[BattlePlayerState]
  ): IO[BattleSettlementBuildContext] =
    for
      playersLine <- BattleFinishProjectionLabelRules.playersLine(state.players)
      finishedAt <- BattleFinishProjectionTimeRules.projectedFinishedAt(state)
      durationMs <- BattleFinishProjectionTimeRules.projectedDuration(state)
    yield BattleSettlementBuildContext(
      orderedPlayers = orderedPlayers,
      placementsByPlayerId = orderedPlayers.zipWithIndex.map { case (player, index) =>
        player.playerId -> BattlePlacement.unsafe(index + 1)
      }.toMap,
      playersLine = playersLine.value,
      finishedAt = finishedAt,
      durationMs = durationMs,
      playerCount = orderedPlayers.length
    )

  private def playableSettlementFor(
    state: BattleAggregateState,
    context: BattleSettlementBuildContext,
    previousRatings: BattlePreviousRatings,
    player: BattlePlayerState
  ): IO[BattleSettlement] = {
    val placement = context.placementsByPlayerId.getOrElse(player.playerId, BattlePlacement.unsafe(context.orderedPlayers.length))
    val survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(player.alive)
    for
      ratingBefore <- previousRatings.ratingBefore(player.handle)
      score <- BattleSettlementScoringRules.placementScore(Some(placement), context.playerCount)
      ratingDelta <- BattleSettlementScoringRules.ratingDelta(score, Some(placement), survivalOutcome)
      finishedAtLabel <- BattleFinishProjectionLabelRules.finishedAtLabel(context.finishedAt)
      resultLabel <- BattleFinishProjectionLabelRules.resultLabel(player, placement)
      modeLabel <- BattleFinishProjectionLabelRules.modeLabel
      mapLabel <- BattleFinishProjectionLabelRules.mapLabel
      highlightLine <- BattleFinishProjectionLabelRules.highlightLine(player, placement, score)
      timelineHint <- BattleFinishProjectionLabelRules.timelineHint(player)
    yield
      val ratingAfter = Rating(ratingBefore.value + ratingDelta.value)
      val result = BattleResultRecord(
        battleId = state.battleId,
        handle = player.handle,
        displayName = player.displayName,
        finishedAt = context.finishedAt,
        finishedAtLabel = finishedAtLabel,
        durationMs = context.durationMs,
        score = Score(score),
        placement = Some(placement),
        survivalOutcome = survivalOutcome,
        ratingBefore = ratingBefore,
        ratingDelta = ratingDelta,
        ratingAfter = ratingAfter,
        resultLabel = resultLabel,
        modeLabel = modeLabel,
        mapLabel = mapLabel,
        highlightLine = highlightLine,
        playersLine = BattlePlayersLine.fromWire(context.playersLine),
        timelineHint = timelineHint,
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
  ): IO[BattleResultRecord] = {
    val handle = PlayerHandle("server")
    for
      ratingBefore <- previousRatings.ratingBefore(handle)
      displayName <- BattleFinishProjectionLabelRules.serverDisplayName
      finishedAtLabel <- BattleFinishProjectionLabelRules.finishedAtLabel(finishedAt)
      resultLabel <- BattleFinishProjectionLabelRules.serverResultLabel
      modeLabel <- BattleFinishProjectionLabelRules.modeLabel
      mapLabel <- BattleFinishProjectionLabelRules.mapLabel
      highlightLine <- BattleFinishProjectionLabelRules.serverHighlightLine(state.battleId)
      timelineHint <- BattleFinishProjectionLabelRules.serverTimelineHint
    yield BattleResultRecord(
      battleId = state.battleId,
      handle = handle,
      displayName = displayName,
      finishedAt = finishedAt,
      finishedAtLabel = finishedAtLabel,
      durationMs = durationMs,
      score = Score(0),
      placement = None,
      survivalOutcome = BattleSurvivalOutcome.Eliminated,
      ratingBefore = ratingBefore,
      ratingDelta = RatingDelta(0),
      ratingAfter = ratingBefore,
      resultLabel = resultLabel,
      modeLabel = modeLabel,
      mapLabel = mapLabel,
      highlightLine = highlightLine,
      playersLine = BattlePlayersLine.fromWire(playersLine),
      timelineHint = timelineHint,
      currentLoadout = None
    )
  }
}
