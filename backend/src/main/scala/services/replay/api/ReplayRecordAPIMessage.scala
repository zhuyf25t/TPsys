package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Score}
import services.battle.microservices.results.objects.result.BattlePlacement
import services.battle.objects.{BattleId, DurationMillis, EpochMillis}
import services.identity.objects.DisplayName
import services.replay.objects.{ReplayFrameCount, ReplayId, ReplayPlaybackAvailability}
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayRecordAPIMessage(
  replayId: Option[ReplayId] = None,
  battleId: Option[BattleId] = None,
  handle: ReplayRecordHandleInput = ReplayRecordHandleInput.Invalid,
  displayName: Option[DisplayName] = None,
  finishedAt: EpochMillis = EpochMillis(0L),
  finishedAtLabel: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  title: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  modeLabel: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  resultLabel: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  mapLabel: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  highlightLine: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  coverLabel: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  playersLine: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  timelineHint: ReplayRecordTextInput = ReplayRecordTextInput.Empty,
  score: Score = Score(0),
  placement: Option[BattlePlacement] = None,
  durationMs: DurationMillis = DurationMillis(0L),
  survivalOutcome: BattleSurvivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(false),
  thumbnailDataUrl: ReplayRecordOptionalTextInput = ReplayRecordOptionalTextInput.Empty,
  currentLoadout: ReplayRecordOptionalTextInput = ReplayRecordOptionalTextInput.Empty,
  frameCount: ReplayFrameCount = ReplayFrameCount.zero,
  playbackAvailability: ReplayPlaybackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(false),
  framesJson: Option[ReplayRecordFramesJsonInput] = None,
  frames: Option[ReplayRecordFramesPayloadInput] = None
) extends APIMessageWithContext[ReplayService, ReplayDetailResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayDetailResponse] =
    ReplayRecordAPIPlanner.plan(service, this)
}

object ReplayRecordAPIMessage {
  import ReplayAPIMessageDecoding.given

  given Decoder[ReplayRecordAPIMessage] =
    deriveDecoder[ReplayRecordAPIMessage]
}
