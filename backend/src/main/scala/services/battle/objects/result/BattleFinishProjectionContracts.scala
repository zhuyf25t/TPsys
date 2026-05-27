package services.battle.objects.result

import services.battle.objects.{BattleAggregateState, BattleArtifactStatus}

enum BattleFinishProjectionOutcome {
  case Projected
  case NotConfigured
  case ResultProjectedReplayFailed(message: String)
  case ResultFailedReplayProjected(message: String)
  case Failed(message: String)
}

object BattleFinishProjectionOutcome {
  /** 中文名：产物状态（artifactStatus）。游戏职责：把结束投影结果转换成前端可见的战报/回放产物状态。 */
  def artifactStatus(value: BattleFinishProjectionOutcome): BattleArtifactStatus =
    value match {
      case BattleFinishProjectionOutcome.Projected                           => BattleArtifactStatus.Ready
      case BattleFinishProjectionOutcome.NotConfigured                       => BattleArtifactStatus.Pending
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(_)      => BattleArtifactStatus.ResultOnlyReady
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(_)      => BattleArtifactStatus.ReplayOnlyReady
      case BattleFinishProjectionOutcome.Failed(_)                           => BattleArtifactStatus.Pending
    }

  /** 中文名：失败信息（failureMessage）。游戏职责：从结束投影结果中提取可记录的失败原因。 */
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
  /** 中文名：投影结束产物（project）。游戏职责：把已结束的权威战斗状态投影成战报、回放等持久化产物。 */
  def project(state: BattleAggregateState): BattleFinishProjectionOutcome
}

object NoopBattleFinishProjector extends BattleFinishProjector {
  /** 中文名：空投影（project）。游戏职责：测试或未配置投影系统时跳过战报/回放生成。 */
  override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
    BattleFinishProjectionOutcome.NotConfigured
}
