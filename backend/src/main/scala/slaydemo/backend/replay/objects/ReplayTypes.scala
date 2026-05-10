package slaydemo.backend.replay.objects

import slaydemo.backend.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, Rating, RatingDelta, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class ReplayId(value: String) extends AnyVal
final case class ReplayCommentId(value: String) extends AnyVal
final case class ReplayTitle private (value: String) extends AnyVal
final case class ReplayFramesJson private (value: String) extends AnyVal
final case class ReplayFrameCount private (value: Int) extends AnyVal

object ReplayTitle {
  def fromWire(value: String): ReplayTitle =
    new ReplayTitle(Option(value).getOrElse(""))
}

object ReplayFramesJson {
  val empty: ReplayFramesJson =
    new ReplayFramesJson("[]")

  def fromNormalized(value: String): ReplayFramesJson =
    new ReplayFramesJson(value)
}

object ReplayFrameCount {
  val zero: ReplayFrameCount =
    new ReplayFrameCount(0)

  def fromWire(value: Int): ReplayFrameCount =
    new ReplayFrameCount(math.max(0, value))
}

final case class ReplaySettlementRecord(
  handle: PlayerHandle,
  displayName: DisplayName,
  resultLabel: String,
  highlightLine: String,
  score: Score,
  placement: Option[BattlePlacement],
  ratingBefore: Option[Rating],
  ratingDelta: Option[RatingDelta],
  ratingAfter: Option[Rating],
  survivalOutcome: BattleSurvivalOutcome,
  currentLoadout: Option[String]
) {
  def aliveAtEnd: Boolean =
    BattleSurvivalOutcome.aliveAtEnd(survivalOutcome)
}

final case class ReplayRecord(
  replayId: ReplayId,
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: String,
  title: ReplayTitle,
  modeLabel: String,
  resultLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Score,
  placement: Option[BattlePlacement],
  ratingBefore: Option[Rating],
  ratingDelta: Option[RatingDelta],
  ratingAfter: Option[Rating],
  durationMs: DurationMillis,
  survivalOutcome: BattleSurvivalOutcome,
  thumbnailDataUrl: Option[String],
  currentLoadout: Option[String],
  frameCount: ReplayFrameCount,
  playbackAvailability: ReplayPlaybackAvailability,
  framesJson: ReplayFramesJson,
  settlements: Vector[ReplaySettlementRecord] = Vector.empty
) {
  def aliveAtEnd: Boolean =
    BattleSurvivalOutcome.aliveAtEnd(survivalOutcome)

  def playbackAvailable: Boolean =
    ReplayPlaybackAvailability.availableFlag(playbackAvailability)

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
