package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] final case class BattleFinishProjectionPreparation(
  storedBattle: StoredBattle,
  projectionCandidate: Option[BattleAggregateState]
)

private[services] object BattleFinishProjectionPreparationRules {
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
