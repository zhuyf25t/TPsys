package services.battle.microservices.abilities.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.runtime.services.BattleTimeRules.*
import services.battle.objects.core.{BattleAggregateState, DurationMillis}

private[battle] object BattleSlowFieldRuntimeRules {
  def advanceSlowFields(state: BattleAggregateState, deltaMs: Long): IO[BattleAggregateState] =
    state.slowFields.traverse { field =>
      decrementLong(field.ttlMs.value, deltaMs).map(ttlMs => field.copy(ttlMs = DurationMillis(ttlMs)))
    }.map { slowFields =>
      state.copy(slowFields = slowFields.filter(_.ttlMs.value > 0L))
    }
}
