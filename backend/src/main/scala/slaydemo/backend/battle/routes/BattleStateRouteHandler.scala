package slaydemo.backend.battle.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.BattleStateService
import slaydemo.backend.battle.routes.BattleRouteHttpSupport.jsonError
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] final class BattleStateRouteHandler(battleStateService: BattleStateService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" if BattleRouteRequestParsers.isStateStreamPath(exchange.getRequestURI.getPath) =>
          handleStateStream(exchange)
        case "GET" =>
          handleStateRead(exchange)
        case _ =>
          jsonError(exchange, BattleRouteErrorMapper.unsupportedState)
      }
    } finally {
      exchange.close()
    }
  }

  private def handleStateRead(exchange: HttpExchange): Unit =
    BattleRouteRequestParsers.stateBattleId(exchange.getRequestURI.getPath, exchange.getRequestURI.getRawQuery) match {
      case None =>
        jsonError(exchange, BattleRouteErrorMapper.invalidBattleId)
      case Some(battleId) =>
        battleStateService.currentState(battleId) match {
          case Right(state) =>
            HttpRouteSupport.sendJson(exchange, 200, BattleStateJson.renderState(state))
          case Left(error) =>
            jsonError(exchange, BattleRouteErrorMapper.stateRead(error))
        }
    }

  private def handleStateStream(exchange: HttpExchange): Unit =
    BattleRouteRequestParsers.stateStreamBattleId(exchange.getRequestURI.getRawQuery) match {
      case None =>
        jsonError(exchange, BattleRouteErrorMapper.invalidBattleId)
      case Some(battleId) =>
        battleStateService.currentState(battleId) match {
          case Left(error) =>
            jsonError(exchange, BattleRouteErrorMapper.stateRead(error))
          case Right(state) =>
            val headers = exchange.getResponseHeaders
            headers.set("Content-Type", "text/event-stream; charset=utf-8")
            headers.set("Cache-Control", "no-cache")
            headers.set("Connection", "keep-alive")
            exchange.sendResponseHeaders(200, 0)
            BattleStateStreamWriter.writeStateFrames(
              output = exchange.getResponseBody,
              battleId = battleId,
              initialState = state,
              nextState = battleId => battleStateService.currentState(battleId).toOption
            )
        }
    }
}

private[routes] object BattleStateRouteHandler {
  def apply(battleStateService: BattleStateService): BattleStateRouteHandler =
    new BattleStateRouteHandler(battleStateService)
}
