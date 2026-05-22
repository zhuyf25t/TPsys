package services.battle.services.runtime

import services.battle.services.*

import services.battle.objects.{BattleAggregateState, BattlePlayerState}

private[services] object BattleAggregateUpdateRules {
  /** 中文名：replace玩家（replacePlayer）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): BattleAggregateState =
    state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing))
}
