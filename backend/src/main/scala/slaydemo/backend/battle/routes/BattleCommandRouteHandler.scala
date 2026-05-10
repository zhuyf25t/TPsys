package slaydemo.backend.battle.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.BattleStateService
import slaydemo.backend.battle.routes.BattleRouteHttpSupport.{handlePost, jsonError, readJsonObject}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] final class BattleCommandRouteHandler(battleStateService: BattleStateService) {
  def handle(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, BattleRouteErrorMapper.badJsonObject(message))
        case Right(fields) =>
          BattleCommandRequestParser.parse(fields) match {
            case Left(error) =>
              jsonError(exchange, BattleRouteErrorMapper.commandRequest(error))
            case Right(request) =>
              battleStateService.acceptCommand(request) match {
                case Right(accepted) =>
                  HttpRouteSupport.sendJson(exchange, 200, BattleStateJson.renderCommandAccepted(accepted))
                case Left(error) =>
                  jsonError(exchange, BattleRouteErrorMapper.commandSubmit(error))
              }
          }
      }
    }
}

private[routes] object BattleCommandRouteHandler {
  def apply(battleStateService: BattleStateService): BattleCommandRouteHandler =
    new BattleCommandRouteHandler(battleStateService)
}
