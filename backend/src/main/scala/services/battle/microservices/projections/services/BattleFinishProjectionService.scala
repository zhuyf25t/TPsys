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
        previousRatings <- previousRatingsFor(
          state.battleId,
          BattleFinishProjectionPlanner.humanPlayersByPlacement(state)
        )
        plan <- IO.pure(BattleFinishProjectionPlanner.build(state, previousRatings))
        resultOutcome <-
          if BattleArtifactStatus.isResultReady(state.artifactStatus) then IO.pure(BattleProjectionArtifactWriteOutcome.Projected)
          else writeArtifact("result", state.battleId, plan, resultArtifactWriter)
        replayOutcome <-
          if BattleArtifactStatus.isReplayReady(state.artifactStatus) then IO.pure(BattleProjectionArtifactWriteOutcome.Projected)
          else writeArtifact("replay", state.battleId, plan, replayArtifactWriter)
        outcome <- IO.pure(BattleProjectionArtifactWriteOutcome.combine(resultOutcome, replayOutcome))
      yield outcome).handleErrorWith {
        case NonFatal(error) =>
          val message = failureMessage(error)
          for
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
        val message = failureMessage(error)
        for
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
    }.map(BattlePreviousRatings.fromRatings)

  private def fetchPreviousRating(battleId: BattleId, handle: PlayerHandle): IO[Rating] =
    IO.blocking {
      PostgresSupport
        .withConnection(connectionSettings)(connection => BattleResultTable.list(connection, Some(handle), None, 25))
        .filterNot(_.battleId == battleId)
        .headOption
        .map(_.ratingAfter)
        .getOrElse(BattleSettlementScoringRules.DefaultRating)
    }

  private def failureMessage(error: Throwable): String = {
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
  ): BattleFinishProjectionOutcome =
    (resultOutcome, replayOutcome) match {
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
    }
}
