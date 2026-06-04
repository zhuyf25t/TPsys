package services.battle.microservices.session.services

import cats.effect.IO

import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.core.BattleAggregateState
import services.battle.microservices.results.objects.result.BattleFinishProjectionStatus

private[battle] final case class BattleFinishProjectionPreparation(
  storedBattle: StoredBattle,
  projectionCandidate: Option[BattleAggregateState]
)

private[battle] object BattleFinishProjectionPreparationRules {
  def prepare(storedBattle: StoredBattle): IO[BattleFinishProjectionPreparation] = IO.pure {
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
