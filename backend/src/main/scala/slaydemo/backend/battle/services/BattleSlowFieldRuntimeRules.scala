package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{BattleAggregateState, DurationMillis}
import slaydemo.backend.battle.services.BattleTimeRules.*

private[services] object BattleSlowFieldRuntimeRules {
  def advanceSlowFields(state: BattleAggregateState, deltaMs: Long): BattleAggregateState =
    state.copy(
      slowFields = state.slowFields
        .map(field => field.copy(ttlMs = DurationMillis(decrementLong(field.ttlMs.value, deltaMs))))
        .filter(_.ttlMs.value > 0L)
    )
}
