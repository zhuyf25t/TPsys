package slaydemo.backend.battle.runtime

import slaydemo.backend.battle.objects.BattleAggregateState
import slaydemo.backend.shared.objects.BattleId

trait BattleRuntime {
  def createBattle(battleId: BattleId): BattleAggregateState
  def advanceTick(state: BattleAggregateState): BattleAggregateState
}
