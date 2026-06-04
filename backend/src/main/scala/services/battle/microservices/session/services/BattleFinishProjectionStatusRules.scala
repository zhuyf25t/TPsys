package services.battle.microservices.session.services

import cats.effect.IO

import services.battle.objects.BattleArtifactStatus
import services.battle.microservices.results.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjectionStatus}

private[battle] object BattleFinishProjectionStatusRules {
  def artifactStatusAfterProjection(
    currentStatus: BattleArtifactStatus,
    outcome: BattleFinishProjectionOutcome
  ): IO[BattleArtifactStatus] =
    IO.pure(BattleArtifactStatus.merge(currentStatus, BattleFinishProjectionOutcome.artifactStatus(outcome)))

  def finishProjectionStatusAfter(
    outcome: BattleFinishProjectionOutcome,
    artifactStatus: BattleArtifactStatus
  ): IO[BattleFinishProjectionStatus] =
    outcome match {
      case BattleFinishProjectionOutcome.Projected =>
        IO.pure(BattleFinishProjectionStatus.Ready)
      case BattleFinishProjectionOutcome.NotConfigured =>
        IO.pure(BattleFinishProjectionStatus.NotConfigured)
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) =>
        readyOrFailedProjectionStatus(artifactStatus, message)
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) =>
        readyOrFailedProjectionStatus(artifactStatus, message)
      case BattleFinishProjectionOutcome.Failed(message) =>
        IO.pure(BattleFinishProjectionStatus.Failed(message))
    }

  private def readyOrFailedProjectionStatus(
    artifactStatus: BattleArtifactStatus,
    message: String
  ): IO[BattleFinishProjectionStatus] =
    IO.pure {
      if artifactStatus == BattleArtifactStatus.Ready then BattleFinishProjectionStatus.Ready
      else BattleFinishProjectionStatus.Failed(message)
    }
}
