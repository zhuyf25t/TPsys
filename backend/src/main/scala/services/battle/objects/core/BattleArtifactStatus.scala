package services.battle.objects.core

enum BattleArtifactStatus {
  case Pending
  case ResultOnlyReady
  case ReplayOnlyReady
  case Ready
}

object BattleArtifactStatus {
  def isResultReady(value: BattleArtifactStatus): Boolean =
    value match {
      case BattleArtifactStatus.Pending         => false
      case BattleArtifactStatus.ResultOnlyReady => true
      case BattleArtifactStatus.ReplayOnlyReady => false
      case BattleArtifactStatus.Ready           => true
    }

  def isReplayReady(value: BattleArtifactStatus): Boolean =
    value match {
      case BattleArtifactStatus.Pending         => false
      case BattleArtifactStatus.ResultOnlyReady => false
      case BattleArtifactStatus.ReplayOnlyReady => true
      case BattleArtifactStatus.Ready           => true
    }

  def fromReadiness(resultReady: Boolean, replayReady: Boolean): BattleArtifactStatus =
    (resultReady, replayReady) match {
      case (false, false) => BattleArtifactStatus.Pending
      case (true, false)  => BattleArtifactStatus.ResultOnlyReady
      case (false, true)  => BattleArtifactStatus.ReplayOnlyReady
      case (true, true)   => BattleArtifactStatus.Ready
    }

  def merge(current: BattleArtifactStatus, update: BattleArtifactStatus): BattleArtifactStatus =
    fromReadiness(
      isResultReady(current) || isResultReady(update),
      isReplayReady(current) || isReplayReady(update)
    )
}
