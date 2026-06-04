package services.battle.microservices.session.services

import cats.effect.IO

import services.battle.microservices.results.objects.result.BattleFinishProjectionOutcome

private[battle] object BattleFinishProjectionCompletionRules {
  def complete(
    storedBattle: StoredBattle,
    outcome: BattleFinishProjectionOutcome
  ): IO[StoredBattle] =
    for
      artifactStatus <- BattleFinishProjectionStatusRules.artifactStatusAfterProjection(
        storedBattle.state.artifactStatus,
        outcome
      )
      finishProjectionStatus <- BattleFinishProjectionStatusRules.finishProjectionStatusAfter(
        outcome,
        artifactStatus
      )
    yield storedBattle.copy(
      state = storedBattle.state.copy(artifactStatus = artifactStatus),
      finishProjectionStatus = finishProjectionStatus
    )
}
