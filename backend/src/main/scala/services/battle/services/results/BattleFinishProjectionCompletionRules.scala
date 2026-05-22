package services.battle.services.results

import services.battle.services.*

private[services] object BattleFinishProjectionCompletionRules {
  /** 中文名：complete（complete）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
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
