package services.battle.database.runtime

import services.battle.objects.event.BattleEventState
import services.battle.objects.projectile.BattleProjectileTerminalState

private[services] object BattleRetentionRules {
  /** 中文名：保留最近投射物终止记录（retainRecentProjectileTerminals）。游戏职责：限制命中/消失等投射物终止记录数量，避免状态无限增长。 */
  def retainRecentProjectileTerminals(
    terminals: Vector[BattleProjectileTerminalState]
  ): Vector[BattleProjectileTerminalState] =
    terminals.takeRight(BattleHistoryCatalog.RetainedProjectileTerminalCount.value)

  /** 中文名：保留最近战斗事件（retainRecentEvents）。游戏职责：限制战斗事件流数量，只保留 HUD 和回放需要的最新事件。 */
  def retainRecentEvents(events: Vector[BattleEventState]): Vector[BattleEventState] =
    events.takeRight(BattleHistoryCatalog.RetainedBattleEventCount.value)
}
