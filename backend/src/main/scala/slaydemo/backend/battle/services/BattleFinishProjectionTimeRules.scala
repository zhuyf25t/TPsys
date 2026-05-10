package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{BattleAggregateState, DurationMillis, EpochMillis}

private[services] object BattleFinishProjectionTimeRules {
  def projectedDuration(state: BattleAggregateState): DurationMillis =
    DurationMillis(clampElapsed(state.elapsedMs.value, state.durationMs.value))

  def projectedFinishedAt(state: BattleAggregateState): EpochMillis = {
    val duration = projectedDuration(state)
    if state.startedAt.value > 0L then EpochMillis(state.startedAt.value + duration.value)
    else state.serverTime
  }

  private def clampElapsed(value: Long, maxValue: Long): Long =
    math.max(0L, math.min(math.max(0L, maxValue), value))
}
