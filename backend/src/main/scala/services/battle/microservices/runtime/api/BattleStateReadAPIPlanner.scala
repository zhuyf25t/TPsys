package services.battle.microservices.runtime.api

import cats.effect.IO

import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.BattleAggregateState

object BattleStateReadAPIPlanner {
  def plan(stateService: BattleStateService, message: BattleStateReadAPIMessage): IO[BattleAggregateState] =
    for
      result <- stateService.currentState(message.battleId)
      state <- BattleRuntimeAPIMessageErrors.stateRead(result)
    yield state
}
