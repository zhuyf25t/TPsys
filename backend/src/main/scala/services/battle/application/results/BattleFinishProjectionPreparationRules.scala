package services.battle.application

import services.battle.application.*

import services.battle.objects.*
import services.battle.application.BattleFinishProjectionStatus
import services.battle.application.StoredBattle

private[services] final case class BattleFinishProjectionPreparation(
  storedBattle: StoredBattle,
  projectionCandidate: Option[BattleAggregateState]
)

private[services] object BattleFinishProjectionPreparationRules {
  /** 中文名：准备（prepare）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
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
