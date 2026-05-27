package services.battle.database.session

import services.battle.objects.BattleArtifactStatus
import services.battle.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjectionStatus}

private[services] object BattleFinishProjectionStatusRules {
  /** 中文名：投影后的产物状态（artifactStatusAfterProjection）。游戏职责：把本次投影结果合并进战斗状态里的战报/回放可用性。 */
  def artifactStatusAfterProjection(
    currentStatus: BattleArtifactStatus,
    outcome: BattleFinishProjectionOutcome
  ): BattleArtifactStatus =
    BattleArtifactStatus.merge(currentStatus, BattleFinishProjectionOutcome.artifactStatus(outcome))

  /** 中文名：投影后的结束状态（finishProjectionStatusAfter）。游戏职责：把战报/回放投影结果转换成 session 保存的结束投影状态。 */
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
