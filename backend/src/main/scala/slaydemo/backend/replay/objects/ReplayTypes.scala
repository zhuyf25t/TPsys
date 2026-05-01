package slaydemo.backend.replay.objects

import slaydemo.backend.battle.objects.{BattleId, DurationMillis, EpochMillis, Rating, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class ReplayId(value: String) extends AnyVal
final case class ReplayCommentId(value: String) extends AnyVal

final case class ReplaySettlementRecord(
  handle: PlayerHandle,
  displayName: DisplayName,
  resultLabel: String,
  highlightLine: String,
  score: Score,
  placement: Option[Int],
  ratingBefore: Option[Rating],
  ratingDelta: Option[Int],
  ratingAfter: Option[Rating],
  aliveAtEnd: Boolean,
  currentLoadout: Option[String]
)

final case class ReplayRecord(
  replayId: ReplayId,
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: String,
  title: String,
  modeLabel: String,
  resultLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Score,
  placement: Option[Int],
  ratingBefore: Option[Rating],
  ratingDelta: Option[Int],
  ratingAfter: Option[Rating],
  durationMs: DurationMillis,
  aliveAtEnd: Boolean,
  thumbnailDataUrl: Option[String],
  currentLoadout: Option[String],
  frameCount: Int,
  playbackAvailable: Boolean,
  framesJson: String,
  settlements: Vector[ReplaySettlementRecord] = Vector.empty
) {
  def settlementFor(handle: PlayerHandle): Option[ReplaySettlementRecord] =
    settlements.find(_.handle.key == handle.key)
}

final case class ReplayCommentRecord(
  id: ReplayCommentId,
  replayId: ReplayId,
  authorHandle: PlayerHandle,
  body: String,
  createdAt: EpochMillis
)
