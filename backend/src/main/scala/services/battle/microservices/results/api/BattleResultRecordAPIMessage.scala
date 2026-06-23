package services.battle.microservices.results.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.results.api.results.BattleResultRecordResponse
import services.battle.objects.core.{
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  DurationMillis,
  EpochMillis
}
import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.battle.microservices.results.objects.result.{
  BattleHighlightLine,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultFinishedAtLabel,
  BattleResultLabel,
  BattleResultLoadoutLabel,
  BattleTimelineHint,
  RatingDelta
}
import services.identity.objects.{DisplayName, PlayerHandle}
import system.api.APIWithTokenMessage
import system.objects.UserId

final case class BattleResultRecordAPIMessage(
  userId: UserId,
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: Option[DisplayName],
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
) extends APIWithTokenMessage[BattleResultRecordResponse] {
  override def plan(connection: Connection): IO[BattleResultRecordResponse] =
    BattleResultRecordAPIPlanner.plan(connection, this)
}

object BattleResultRecordAPIMessage {
  import BattleResultAPIMessageDecoding.given

  given Decoder[BattleResultRecordAPIMessage] =
    deriveDecoder[BattleResultRecordAPIMessage]
}
