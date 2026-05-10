package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{BattleAggregateState, BattlePlayerState}

private[services] object BattleAggregateUpdateRules {
  def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): BattleAggregateState =
    state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing))
}
