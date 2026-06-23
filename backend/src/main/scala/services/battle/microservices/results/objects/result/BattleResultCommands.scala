package services.battle.microservices.results.objects.result

import services.battle.objects.core.{
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  DurationMillis,
  EpochMillis
}
import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.identity.objects.{DisplayName, PlayerHandle}

final case class BattleResultRecordCommand(
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: BattleResultFinishedAtLabel,
  durationMs: DurationMillis,
  score: Score,
  placement: Option[BattlePlacement],
  survivalOutcome: BattleSurvivalOutcome,
  ratingBefore: Rating,
  ratingDelta: RatingDelta,
  ratingAfter: Rating,
  resultLabel: BattleResultLabel,
  modeLabel: BattleModeLabel,
  mapLabel: BattleMapLabel,
  highlightLine: BattleHighlightLine,
  playersLine: BattlePlayersLine,
  timelineHint: BattleTimelineHint,
  currentLoadout: Option[BattleResultLoadoutLabel]
)

final case class BattleResultListQuery(
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: BattleResultListLimit
)

enum BattleResultRecordValidationError {
  case InvalidHandle
  case VisitorNotAllowed
}
