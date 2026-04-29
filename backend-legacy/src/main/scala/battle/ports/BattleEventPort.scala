package slaydemo.backend.battle.ports

import slaydemo.backend.shared.objects.BattleId

trait BattleEventPort {
  def publishBattleEvent(battleId: BattleId, eventType: String, payload: String): Unit
}
