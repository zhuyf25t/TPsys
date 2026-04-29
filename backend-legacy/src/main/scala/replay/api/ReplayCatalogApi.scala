package slaydemo.backend.replay.api

import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final case class ReplaySubmissionRequest(
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
  framesJson: String
)

final case class ReplayCatalogView(
  replayId: ReplayId,
  battleId: BattleId,
  handle: UserId,
  title: String,
  modeLabel: String,
  resultLabel: String,
  finishedAt: Long,
  finishedAtLabel: String,
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
  frameCount: Int,
  playbackAvailable: Boolean
)

final case class ReplayDetailView(
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
  framesJson: String
)

final case class ReplayCommentSubmissionRequest(
  replayId: ReplayId,
  authorHandle: UserId,
  body: String
)

final case class ReplayCommentView(
  id: String,
  replayId: ReplayId,
  authorHandle: UserId,
  body: String,
  createdAt: Long
)

trait ReplayCatalogApi {
  def submit(request: ReplaySubmissionRequest): Either[String, ReplayDetailView]
  def list(limit: Int): Vector[ReplayCatalogView]
  def load(replayId: ReplayId): Option[ReplayDetailView]
}
