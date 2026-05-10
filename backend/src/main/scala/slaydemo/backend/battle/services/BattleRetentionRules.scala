package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleRetentionRules {
  def retainRecentProjectileTerminals(
    terminals: Vector[BattleProjectileTerminalState]
  ): Vector[BattleProjectileTerminalState] =
    terminals.takeRight(BattleHistoryCatalog.RetainedProjectileTerminalCount.value)

  def retainRecentEvents(events: Vector[BattleEventState]): Vector[BattleEventState] =
    events.takeRight(BattleHistoryCatalog.RetainedBattleEventCount.value)
}
