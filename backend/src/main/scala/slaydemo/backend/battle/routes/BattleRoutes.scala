package slaydemo.backend.battle.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.BattleStateService

final class BattleRoutes(battleStateService: BattleStateService) {
  private val stateRouteHandler = BattleStateRouteHandler(battleStateService)

  def state(exchange: HttpExchange): Unit =
    stateRouteHandler.handle(exchange)
}

object BattleRoutes {
  def apply(battleStateService: BattleStateService): BattleRoutes =
    new BattleRoutes(battleStateService)
}
