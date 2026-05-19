package slaydemo.backend.battle.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.{BattleResultRecordError, BattleResultService}
import slaydemo.backend.shared.api.BackendAPIEndpoint
import slaydemo.backend.shared.routes.HttpRouteSupport

final class BattleResultRoutes(service: BattleResultService) {
  def apiEndpoints: Vector[BackendAPIEndpoint] =
    Vector(BattleResultsAPIMessagePlanner.endpoint(service))

  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" =>
          handleList(exchange)
        case "POST" =>
          handleRecord(exchange)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET, POST, HEAD, and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def handleList(exchange: HttpExchange): Unit = {
    BattleResultCommandParsers.parseListRequest(exchange.getRequestURI.getRawQuery) match {
      case BattleResultListRequestParseResult.EmptyResults =>
        HttpRouteSupport.sendJson(exchange, 200, BattleResultRouteJsonRenderer.renderRecords(Vector.empty))
      case BattleResultListRequestParseResult.Query(request) =>
        val results = service.list(
          handle = request.handle,
          battleId = request.battleId,
          limit = request.limit
        )
        HttpRouteSupport.sendJson(exchange, 200, BattleResultRouteJsonRenderer.renderRecords(results))
    }
  }

  private def handleRecord(exchange: HttpExchange): Unit =
    ResultJsonObjectParser.parse(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object.")
      case Right(fields) =>
        BattleResultCommandParsers.parseRecordCommand(fields) match {
          case Right(command) =>
            service.record(command) match {
              case Right(record) =>
                HttpRouteSupport.sendJson(exchange, 201, BattleResultRouteJsonRenderer.renderRecord(record))
              case Left(BattleResultRecordError.InvalidHandle) =>
                jsonError(exchange, 400, "invalid_handle", "invalid_handle")
              case Left(BattleResultRecordError.VisitorNotAllowed) =>
                jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
            }
          case Left(BattleResultRecordCommandParseError.InvalidBattleId) =>
            jsonError(exchange, 400, "invalid_battle_id", "invalid_battle_id")
          case Left(BattleResultRecordCommandParseError.InvalidHandle) =>
            jsonError(exchange, 400, "invalid_handle", "invalid_handle")
          case Left(BattleResultRecordCommandParseError.VisitorNotAllowed) =>
            jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
        }
    }

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(
      exchange,
      status,
      s"""{"error":${jsonString(message)},"code":${jsonString(code)}}"""
    )
}

object BattleResultRoutes {
  def apply(service: BattleResultService): BattleResultRoutes =
    new BattleResultRoutes(service)
}
