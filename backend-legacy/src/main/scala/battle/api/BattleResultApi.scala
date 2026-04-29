package slaydemo.backend.battle.api

import slaydemo.backend.shared.objects.{BattleId, UserId}

final case class BattleResultSubmissionRequest(
  battleId: BattleId,
  handle: UserId,
  displayName: String,
  finishedAt: Long,
  finishedAtLabel: String,
  durationMs: Long,
  score: Int,
  placement: Option[Int],
  aliveAtEnd: Boolean,
  ratingBefore: Int,
  ratingDelta: Int,
  ratingAfter: Int,
  resultLabel: String,
  modeLabel: String,
  mapLabel: String,
  highlightLine: String,
  playersLine: String,
  timelineHint: String,
  currentLoadout: Option[String]
)

