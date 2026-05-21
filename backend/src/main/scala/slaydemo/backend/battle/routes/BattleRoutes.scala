package slaydemo.backend.battle.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.{
  BattleQueueService,
  BattleStateService
}

final class BattleRoutes(
  queueService: BattleQueueService,
  battleStateService: BattleStateService
) {
  private val roomRouteHandler = BattleRoomRouteHandler(queueService)
  private val stateRouteHandler = BattleStateRouteHandler(battleStateService)

  def rooms(exchange: HttpExchange): Unit =
    roomRouteHandler.handle(exchange)

  def state(exchange: HttpExchange): Unit =
    stateRouteHandler.handle(exchange)
}

object BattleRoutes {
  def apply(
    queueService: BattleQueueService,
    battleStateService: BattleStateService
  ): BattleRoutes =
    new BattleRoutes(queueService, battleStateService)
}
