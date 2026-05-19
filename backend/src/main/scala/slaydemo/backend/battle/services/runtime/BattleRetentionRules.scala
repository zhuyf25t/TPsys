package slaydemo.backend.battle.services.runtime

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.*

private[services] object BattleRetentionRules {
  /** 中文名：保留recent投射物terminals（retainRecentProjectileTerminals）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def retainRecentProjectileTerminals(
    terminals: Vector[BattleProjectileTerminalState]
  ): Vector[BattleProjectileTerminalState] =
    terminals.takeRight(BattleHistoryCatalog.RetainedProjectileTerminalCount.value)

  /** 中文名：保留recentevents（retainRecentEvents）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def retainRecentEvents(events: Vector[BattleEventState]): Vector[BattleEventState] =
    events.takeRight(BattleHistoryCatalog.RetainedBattleEventCount.value)
}
