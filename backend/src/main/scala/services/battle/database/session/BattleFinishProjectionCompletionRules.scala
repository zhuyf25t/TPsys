package services.battle.database.session

import services.battle.objects.result.BattleFinishProjectionOutcome

private[services] object BattleFinishProjectionCompletionRules {
  /** 中文名：完成结束投影（complete）。游戏职责：把战报/回放生成结果写回 session 中保存的权威战斗状态。 */
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
