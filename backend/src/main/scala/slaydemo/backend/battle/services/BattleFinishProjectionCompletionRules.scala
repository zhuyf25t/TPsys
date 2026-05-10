package slaydemo.backend.battle.services

private[services] object BattleFinishProjectionCompletionRules {
  def complete(
    storedBattle: StoredBattle,
    outcome: BattleFinishProjectionOutcome
  ): StoredBattle = {
    val artifactStatus = BattleFinishProjectionStatusRules.artifactStatusAfterProjection(
      storedBattle.state.artifactStatus,
      outcome
    )
    storedBattle.copy(
      state = storedBattle.state.copy(artifactStatus = artifactStatus),
      finishProjectionStatus = BattleFinishProjectionStatusRules.finishProjectionStatusAfter(
        outcome,
        artifactStatus
      )
    )
  }
}
