package services.battle.microservices.session.services

import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.core.BattleAggregateState
import services.battle.microservices.results.objects.result.BattleFinishProjectionStatus

private[battle] final case class BattleFinishProjectionPreparation(
  storedBattle: StoredBattle,
  projectionCandidate: Option[BattleAggregateState]
)

private[battle] object BattleFinishProjectionPreparationRules {
  /** 中文名：准备结束投影（prepare）。游戏职责：在战斗刚结束时标记投影进行中，并交出需要生成战�?回放的权威状态�?*/
  def prepare(storedBattle: StoredBattle): BattleFinishProjectionPreparation = {
    val state = storedBattle.state
    if state.phase != BattlePhase.Finished then BattleFinishProjectionPreparation(storedBattle, None)
    else
      storedBattle.finishProjectionStatus match {
        case BattleFinishProjectionStatus.Ready =>
          BattleFinishProjectionPreparation(
            storedBattle.copy(state = state.copy(artifactStatus = BattleArtifactStatus.Ready)),
            None
          )
        case BattleFinishProjectionStatus.InProgress | BattleFinishProjectionStatus.NotConfigured =>
          BattleFinishProjectionPreparation(storedBattle, None)
        case BattleFinishProjectionStatus.Pending | BattleFinishProjectionStatus.Failed(_) =>
          BattleFinishProjectionPreparation(
            storedBattle.copy(finishProjectionStatus = BattleFinishProjectionStatus.InProgress),
            Some(state)
          )
      }
  }
}
