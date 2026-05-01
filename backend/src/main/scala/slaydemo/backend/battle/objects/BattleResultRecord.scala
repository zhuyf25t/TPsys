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
  placement: Option[Int],
  aliveAtEnd: Boolean,
  ratingBefore: Rating,
  ratingDelta: Int,
  ratingAfter: Rating,
  resultLabel: String,
  modeLabel: String,
  mapLabel: String,
  highlightLine: String,
  playersLine: String,
  timelineHint: String,
  currentLoadout: Option[String]
) {
  def resultId: BattleResultId =
    BattleResultRecord.resultId(battleId, handle)
}

object BattleResultRecord {
  def resultId(battleId: BattleId, handle: PlayerHandle): BattleResultId =
    BattleResultId(s"${battleId.value.trim}:${handle.key}")
}
