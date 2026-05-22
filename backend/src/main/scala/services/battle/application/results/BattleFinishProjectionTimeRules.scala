package services.battle.application

import services.battle.application.*

import services.battle.objects.{BattleAggregateState, DurationMillis, EpochMillis}

private[services] object BattleFinishProjectionTimeRules {
  /** 中文名：projectedduration（projectedDuration）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def projectedDuration(state: BattleAggregateState): DurationMillis =
    DurationMillis(clampElapsed(state.elapsedMs.value, state.durationMs.value))

  /** 中文名：projected已结束at（projectedFinishedAt）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def projectedFinishedAt(state: BattleAggregateState): EpochMillis = {
    val duration = projectedDuration(state)
    if state.startedAt.value > 0L then EpochMillis(state.startedAt.value + duration.value)
    else state.serverTime
  }

  private def clampElapsed(value: Long, maxValue: Long): Long =
    math.max(0L, math.min(math.max(0L, maxValue), value))
}
