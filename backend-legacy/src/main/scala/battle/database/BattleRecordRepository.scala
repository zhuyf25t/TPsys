package slaydemo.backend.battle.database

import slaydemo.backend.battle.objects.BattleAggregateState
import slaydemo.backend.shared.objects.BattleId

trait BattleRecordRepository {
  def findState(battleId: BattleId): Option[BattleAggregateState]
}
