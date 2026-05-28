package services.battle.microservices.projections.services

import scala.util.control.NonFatal

import services.battle.database.results.BattleResultTable
import services.battle.microservices.projections.services.{BattleMailPublisherPort, BattleReplayWriterPort}
import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.core.{BattleAggregateState, BattleId, Rating}
import services.battle.objects.player.BattlePlayerState
import services.battle.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjector}
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

  /** 中文名：project（project）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
    if state.phase != BattlePhase.Finished then BattleFinishProjectionOutcome.NotConfigured
    else
      try {
        val previousRatings = previousRatingsFor(
          state.battleId,
          BattleFinishProjectionPlanner.humanPlayersByPlacement(state)
        )
        val plan = BattleFinishProjectionPlanner.build(state, previousRatings)
        val resultOutcome =
          if BattleArtifactStatus.isResultReady(state.artifactStatus) then BattleProjectionArtifactWriteOutcome.Projected
          else writeArtifact("result", state.battleId, plan, resultArtifactWriter)
        val replayOutcome =
          if BattleArtifactStatus.isReplayReady(state.artifactStatus) then BattleProjectionArtifactWriteOutcome.Projected
          else writeArtifact("replay", state.battleId, plan, replayArtifactWriter)
        BattleProjectionArtifactWriteOutcome.combine(resultOutcome, replayOutcome)
      } catch {
        case NonFatal(error) =>
          val message = failureMessage(error)
          failureReporter.reportFailure(state.battleId, message)
          BattleFinishProjectionOutcome.Failed(message)
      }

  private def writeArtifact(
    label: String,
    battleId: BattleId,
    plan: BattleFinishProjectionPlan,
    writer: BattleFinishProjectionArtifactWriter
  ): BattleProjectionArtifactWriteOutcome =
    catchArtifactWriteFailure(label, battleId)(writer.write(plan))

  private def catchArtifactWriteFailure(
    label: String,
    battleId: BattleId
  )(write: => Unit): BattleProjectionArtifactWriteOutcome =
    try {
      write
      BattleProjectionArtifactWriteOutcome.Projected
    } catch {
      case NonFatal(error) =>
        val message = failureMessage(error)
        failureReporter.reportFailure(battleId, s"$label: $message")
        BattleProjectionArtifactWriteOutcome.Failed(message)
    }

  private def previousRatingsFor(
    battleId: BattleId,
    players: Vector[BattlePlayerState]
  ): BattlePreviousRatings =
    BattlePreviousRatings.fromRatings(
      players.map(player => player.handle -> fetchPreviousRating(battleId, player.handle))
    )

  private def fetchPreviousRating(battleId: BattleId, handle: PlayerHandle): Rating =
    PostgresSupport
      .withConnection(connectionSettings)(connection => BattleResultTable.list(connection, Some(handle), None, 25))
      .filterNot(_.battleId == battleId)
      .headOption
      .map(_.ratingAfter)
      .getOrElse(BattleSettlementScoringRules.DefaultRating)

  private def failureMessage(error: Throwable): String = {
    val detail = Option(error.getMessage).map(_.trim).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
    s"${error.getClass.getSimpleName}: $detail"
  }
}

object DefaultBattleFinishProjector {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
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
  /** 中文名：combine（combine）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
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
