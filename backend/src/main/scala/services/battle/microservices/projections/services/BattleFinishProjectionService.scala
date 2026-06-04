package services.battle.microservices.projections.services

import scala.util.control.NonFatal

import cats.effect.IO

import services.battle.microservices.results.database.BattleResultTable
import services.battle.microservices.projections.services.{BattleMailPublisherPort, BattleReplayWriterPort}
import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.core.{BattleAggregateState, BattleId}
import services.battle.microservices.actors.objects.player.{BattlePlayerState, Rating}
import services.battle.microservices.results.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjector}
import services.identity.objects.PlayerHandle
import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

final class DefaultBattleFinishProjector(
  connectionSettings: PostgresConnectionSettings,
  replayWriter: BattleReplayWriterPort,
  mailPublisher: BattleMailPublisherPort,
  failureReporter: BattleFinishProjectionFailureReporter = ConsoleBattleFinishProjectionFailureReporter
) extends BattleFinishProjector {
  private val resultArtifactWriter =
    BattleResultProjectionArtifactWriter(connectionSettings, mailPublisher)
  private val replayArtifactWriter =
    BattleReplayProjectionArtifactWriter(replayWriter)

  override def project(state: BattleAggregateState): IO[BattleFinishProjectionOutcome] =
    if state.phase != BattlePhase.Finished then IO.pure(BattleFinishProjectionOutcome.NotConfigured)
    else
      (for
        humanPlayers <- BattleFinishProjectionPlanner.humanPlayersByPlacement(state)
        previousRatings <- previousRatingsFor(
          state.battleId,
          humanPlayers
        )
        plan <- BattleFinishProjectionPlanner.build(state, previousRatings)
        resultOutcome <-
          if BattleArtifactStatus.isResultReady(state.artifactStatus) then IO.pure(BattleProjectionArtifactWriteOutcome.Projected)
          else writeArtifact("result", state.battleId, plan, resultArtifactWriter)
        replayOutcome <-
          if BattleArtifactStatus.isReplayReady(state.artifactStatus) then IO.pure(BattleProjectionArtifactWriteOutcome.Projected)
          else writeArtifact("replay", state.battleId, plan, replayArtifactWriter)
        outcome <- BattleProjectionArtifactWriteOutcome.combine(resultOutcome, replayOutcome)
      yield outcome).handleErrorWith {
        case NonFatal(error) =>
          for
            message <- failureMessage(error)
            _ <- failureReporter.reportFailure(state.battleId, message)
          yield BattleFinishProjectionOutcome.Failed(message)
      }

  private def writeArtifact(
    label: String,
    battleId: BattleId,
    plan: BattleFinishProjectionPlan,
    writer: BattleFinishProjectionArtifactWriter
  ): IO[BattleProjectionArtifactWriteOutcome] =
    catchArtifactWriteFailure(label, battleId)(writer.write(plan))

  private def catchArtifactWriteFailure(
    label: String,
    battleId: BattleId
  )(write: IO[Unit]): IO[BattleProjectionArtifactWriteOutcome] =
    (for
      _ <- write
    yield BattleProjectionArtifactWriteOutcome.Projected).handleErrorWith {
      case NonFatal(error) =>
        for
          message <- failureMessage(error)
          _ <- failureReporter.reportFailure(battleId, s"$label: $message")
        yield BattleProjectionArtifactWriteOutcome.Failed(message)
    }

  private def previousRatingsFor(
    battleId: BattleId,
    players: Vector[BattlePlayerState]
  ): IO[BattlePreviousRatings] =
    players.foldLeft(IO.pure(Vector.empty[(PlayerHandle, Rating)])) { case (previous, player) =>
      for
        ratings <- previous
        rating <- fetchPreviousRating(battleId, player.handle)
      yield ratings :+ (player.handle -> rating)
    }.flatMap(BattlePreviousRatings.fromRatings)

  private def fetchPreviousRating(battleId: BattleId, handle: PlayerHandle): IO[Rating] =
    PostgresSupport
      .withConnectionIO(connectionSettings)(connection => BattleResultTable.list(connection, Some(handle), None, 25))
      .map {
        _.filterNot(_.battleId == battleId)
        .headOption
        .map(_.ratingAfter)
        .getOrElse(BattleSettlementScoringRules.DefaultRating)
      }

  private def failureMessage(error: Throwable): IO[String] = IO.pure {
    val detail = Option(error.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    s"${error.getClass.getSimpleName}: $detail"
  }
}

object DefaultBattleFinishProjector {
  def apply(
    connectionSettings: PostgresConnectionSettings,
    replayWriter: BattleReplayWriterPort,
    mailPublisher: BattleMailPublisherPort
  ): DefaultBattleFinishProjector =
    new DefaultBattleFinishProjector(connectionSettings, replayWriter, mailPublisher)
}

private[battle] enum BattleProjectionArtifactWriteOutcome {
  case Projected
  case Failed(message: String)
}

private[battle] object BattleProjectionArtifactWriteOutcome {
  def combine(
    resultOutcome: BattleProjectionArtifactWriteOutcome,
    replayOutcome: BattleProjectionArtifactWriteOutcome
  ): IO[BattleFinishProjectionOutcome] =
    IO.pure((resultOutcome, replayOutcome) match {
      case (BattleProjectionArtifactWriteOutcome.Projected, BattleProjectionArtifactWriteOutcome.Projected) =>
        BattleFinishProjectionOutcome.Projected
      case (BattleProjectionArtifactWriteOutcome.Projected, BattleProjectionArtifactWriteOutcome.Failed(message)) =>
        BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message)
      case (BattleProjectionArtifactWriteOutcome.Failed(message), BattleProjectionArtifactWriteOutcome.Projected) =>
        BattleFinishProjectionOutcome.ResultFailedReplayProjected(message)
      case (
            BattleProjectionArtifactWriteOutcome.Failed(resultMessage),
            BattleProjectionArtifactWriteOutcome.Failed(replayMessage)
          ) =>
        BattleFinishProjectionOutcome.Failed(s"result: $resultMessage; replay: $replayMessage")
    })
}
