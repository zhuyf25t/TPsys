package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.BattleAggregateState
import slaydemo.backend.shared.objects.BattleId

trait BattleService {
  def currentState(battleId: BattleId): Option[BattleAggregateState]
}
