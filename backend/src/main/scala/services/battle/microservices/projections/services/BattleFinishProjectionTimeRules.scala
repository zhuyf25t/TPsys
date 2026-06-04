package services.battle.microservices.projections.services

import cats.effect.IO

import services.battle.objects.{BattleAggregateState, DurationMillis, EpochMillis}

private[battle] object BattleFinishProjectionTimeRules {
  def projectedDuration(state: BattleAggregateState): IO[DurationMillis] =
    clampElapsed(state.elapsedMs.value, state.durationMs.value).map(DurationMillis.apply)

  def projectedFinishedAt(state: BattleAggregateState): IO[EpochMillis] =
    projectedDuration(state).map { duration =>
      if state.startedAt.value > 0L then EpochMillis(state.startedAt.value + duration.value)
      else state.serverTime
    }

  private def clampElapsed(value: Long, maxValue: Long): IO[Long] =
    IO.pure(math.max(0L, math.min(math.max(0L, maxValue), value)))
}
