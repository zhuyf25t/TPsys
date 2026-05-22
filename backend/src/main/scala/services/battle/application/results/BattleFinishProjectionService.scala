package services.battle.application

import services.battle.application.*

import scala.util.control.NonFatal

import services.battle.persistence.BattleResultRepository
import services.battle.ports.{BattleMailPublisherPort, BattleReplayWriterPort}
import services.battle.objects.*
import services.identity.objects.PlayerHandle

enum BattleFinishProjectionOutcome {
  case Projected
  case NotConfigured
  case ResultProjectedReplayFailed(message: String)
  case ResultFailedReplayProjected(message: String)
  case Failed(message: String)
}

object BattleFinishProjectionOutcome {
  /** 中文名：产物状态（artifactStatus）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def artifactStatus(value: BattleFinishProjectionOutcome): BattleArtifactStatus =
    value match {
      case BattleFinishProjectionOutcome.Projected                           => BattleArtifactStatus.Ready
      case BattleFinishProjectionOutcome.NotConfigured                       => BattleArtifactStatus.Pending
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(_)      => BattleArtifactStatus.ResultOnlyReady
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(_)      => BattleArtifactStatus.ReplayOnlyReady
      case BattleFinishProjectionOutcome.Failed(_)                           => BattleArtifactStatus.Pending
    }

  /** 中文名：failuremessage（failureMessage）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def failureMessage(value: BattleFinishProjectionOutcome): Option[String] =
    value match {
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) => Some(message)
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) => Some(message)
      case BattleFinishProjectionOutcome.Failed(message)                      => Some(message)
      case BattleFinishProjectionOutcome.Projected | BattleFinishProjectionOutcome.NotConfigured =>
        None
    }
}

trait BattleFinishProjector {
  /** 中文名：project（project）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def project(state: BattleAggregateState): BattleFinishProjectionOutcome
}

object NoopBattleFinishProjector extends BattleFinishProjector {
  /** 中文名：project（project）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
    BattleFinishProjectionOutcome.NotConfigured
}

final class DefaultBattleFinishProjector(
  battleResultRepository: BattleResultRepository,
  replayWriter: BattleReplayWriterPort,
  mailPublisher: BattleMailPublisherPort,
  failureReporter: BattleFinishProjectionFailureReporter = ConsoleBattleFinishProjectionFailureReporter
) extends BattleFinishProjector {
  private val resultArtifactWriter =
    BattleResultProjectionArtifactWriter(battleResultRepository, mailPublisher)
  private val replayArtifactWriter =
    BattleReplayProjectionArtifactWriter(replayWriter)

  /** 中文名：project（project）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
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
    battleResultRepository
      .list(Some(handle), None, 25)
      .filterNot(_.battleId == battleId)
      .headOption
      .map(_.ratingAfter)
      .getOrElse(BattleSettlementScoringRules.DefaultRating)

  private def failureMessage(error: Throwable): String = {
    BattleFailureMessageFormatter.throwableMessage(error)
  }
}

object DefaultBattleFinishProjector {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def apply(
    battleResultRepository: BattleResultRepository,
    replayWriter: BattleReplayWriterPort,
    mailPublisher: BattleMailPublisherPort
  ): DefaultBattleFinishProjector =
    new DefaultBattleFinishProjector(battleResultRepository, replayWriter, mailPublisher)
}

private[services] enum BattleProjectionArtifactWriteOutcome {
  case Projected
  case Failed(message: String)
}

private[services] object BattleProjectionArtifactWriteOutcome {
  /** 中文名：combine（combine）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
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
