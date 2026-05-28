package services.battle.objects

import services.identity.objects.{DisplayName, PlayerHandle}

final case class BattleStateReadQuery(battleId: BattleId)

final case class BattleResultRecordCommand(
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: String,
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
  currentLoadout: Option[String]
)

final case class BattleResultListQuery(
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: BattleResultListLimit
)
