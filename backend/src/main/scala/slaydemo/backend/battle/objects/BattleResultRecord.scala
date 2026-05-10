package slaydemo.backend.battle.objects

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BattleResultRecord(
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
) {
  def resultId: BattleResultId =
    BattleResultRecord.resultId(battleId, handle)

  def aliveAtEnd: Boolean =
    BattleSurvivalOutcome.aliveAtEnd(survivalOutcome)
}

object BattleResultRecord {
  def resultId(battleId: BattleId, handle: PlayerHandle): BattleResultId =
    BattleResultId(s"${battleId.value.trim}:${handle.key}")
}
