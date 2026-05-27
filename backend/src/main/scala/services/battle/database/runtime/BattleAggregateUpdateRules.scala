package services.battle.database.runtime

import services.battle.objects.{BattleAggregateState, BattlePlayerState}

private[services] object BattleAggregateUpdateRules {
  /** 中文名：替换玩家（replacePlayer）。游戏职责：用更新后的玩家状态替换权威战斗聚合中的同一玩家。 */
  def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): BattleAggregateState =
    state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing))
}
