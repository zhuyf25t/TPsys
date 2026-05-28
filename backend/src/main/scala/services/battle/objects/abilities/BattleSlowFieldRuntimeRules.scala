package services.battle.objects.abilities

import services.battle.objects.runtime.BattleTimeRules.*
import services.battle.objects.core.{BattleAggregateState, DurationMillis}

private[battle] object BattleSlowFieldRuntimeRules {
  /** 中文名：推进减速fields（advanceSlowFields）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互�?*/
  def advanceSlowFields(state: BattleAggregateState, deltaMs: Long): BattleAggregateState =
    state.copy(
      slowFields = state.slowFields
        .map(field => field.copy(ttlMs = DurationMillis(decrementLong(field.ttlMs.value, deltaMs))))
        .filter(_.ttlMs.value > 0L)
    )
}
