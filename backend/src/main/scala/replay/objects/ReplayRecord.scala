package slaydemo.backend.replay.objects

import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final case class ReplayRecord(
  replayId: ReplayId,
  battleId: BattleId,
  handle: UserId,
  displayName: String,
  finishedAt: Long,
  finishedAtLabel: String,
  title: String,
  modeLabel: String,
  resultLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Int,
  placement: Option[Int],
  durationMs: Long,
  aliveAtEnd: Boolean,
  thumbnailDataUrl: Option[String],
  currentLoadout: Option[String],
  frameCount: Int,
  playbackAvailable: Boolean,
  framesJsonB64: String
)
