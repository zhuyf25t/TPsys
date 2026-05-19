package slaydemo.backend.battle.services.results

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.BattleArtifactStatus

private[services] enum BattleFinishProjectionStatus {
  case Pending
  case InProgress
  case Ready
  case NotConfigured
  case Failed(message: String)
}

private[services] object BattleFinishProjectionStatusRules {
  /** 中文名：产物状态after投影（artifactStatusAfterProjection）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def artifactStatusAfterProjection(
    currentStatus: BattleArtifactStatus,
    outcome: BattleFinishProjectionOutcome
  ): BattleArtifactStatus =
    BattleArtifactStatus.merge(currentStatus, BattleFinishProjectionOutcome.artifactStatus(outcome))

  /** 中文名：结束投影状态after（finishProjectionStatusAfter）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
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
