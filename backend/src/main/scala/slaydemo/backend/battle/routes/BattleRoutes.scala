package slaydemo.backend.battle.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleStateService
}

final class BattleRoutes(
  queueService: BattleQueueService,
  battleStateService: BattleStateService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService
) {
  private val commandRouteHandler = BattleCommandRouteHandler(battleStateService)
  private val queueRouteHandler = BattleQueueRouteHandler(queueService, joinAuthorizationService)
  private val roomRouteHandler = BattleRoomRouteHandler(queueService)
  private val stateRouteHandler = BattleStateRouteHandler(battleStateService)

  def join(exchange: HttpExchange): Unit =
    queueRouteHandler.join(exchange)

  def status(exchange: HttpExchange): Unit =
    queueRouteHandler.status(exchange)

  def leave(exchange: HttpExchange): Unit =
    queueRouteHandler.leave(exchange)

  def rooms(exchange: HttpExchange): Unit =
    roomRouteHandler.handle(exchange)

  def state(exchange: HttpExchange): Unit =
    stateRouteHandler.handle(exchange)

  def commands(exchange: HttpExchange): Unit =
    commandRouteHandler.handle(exchange)
}

object BattleRoutes {
  def apply(
    queueService: BattleQueueService,
    battleStateService: BattleStateService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService
  ): BattleRoutes =
    new BattleRoutes(queueService, battleStateService, joinAuthorizationService)
}
