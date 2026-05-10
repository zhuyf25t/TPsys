package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.BattleArtifactStatus

private[services] enum BattleFinishProjectionStatus {
  case Pending
  case InProgress
  case Ready
  case NotConfigured
  case Failed(message: String)
}

private[services] object BattleFinishProjectionStatusRules {
  def artifactStatusAfterProjection(
    currentStatus: BattleArtifactStatus,
    outcome: BattleFinishProjectionOutcome
  ): BattleArtifactStatus =
    BattleArtifactStatus.merge(currentStatus, BattleFinishProjectionOutcome.artifactStatus(outcome))

  def finishProjectionStatusAfter(
    outcome: BattleFinishProjectionOutcome,
    artifactStatus: BattleArtifactStatus
  ): BattleFinishProjectionStatus =
    outcome match {
      case BattleFinishProjectionOutcome.Projected =>
        BattleFinishProjectionStatus.Ready
      case BattleFinishProjectionOutcome.NotConfigured =>
        BattleFinishProjectionStatus.NotConfigured
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) =>
        readyOrFailedProjectionStatus(artifactStatus, message)
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) =>
        readyOrFailedProjectionStatus(artifactStatus, message)
      case BattleFinishProjectionOutcome.Failed(message) =>
        BattleFinishProjectionStatus.Failed(message)
    }

  private def readyOrFailedProjectionStatus(
    artifactStatus: BattleArtifactStatus,
    message: String
  ): BattleFinishProjectionStatus =
    if artifactStatus == BattleArtifactStatus.Ready then BattleFinishProjectionStatus.Ready
    else BattleFinishProjectionStatus.Failed(message)
}
