package slaydemo.backend.battle.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.objects.apiTypes.{
  BattleResultApiCodec,
  BattleResultErrorResponse,
  BattleResultListQueryDecodeResult,
  BattleResultRecordDecodeError
}
import slaydemo.backend.battle.services.{BattleResultRecordError, BattleResultService}
import slaydemo.backend.shared.routes.HttpRouteSupport

final class BattleResultRoutes(service: BattleResultService) {
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
    BattleResultApiCodec.parseListRequest(exchange.getRequestURI.getRawQuery) match {
      case BattleResultListQueryDecodeResult.EmptyResults =>
        HttpRouteSupport.sendJson(exchange, 200, BattleResultApiCodec.renderRecords(Vector.empty))
      case BattleResultListQueryDecodeResult.Query(request) =>
        val results = service.list(
          handle = request.handle,
          battleId = request.battleId,
          limit = request.limit
        )
        HttpRouteSupport.sendJson(exchange, 200, BattleResultApiCodec.renderRecords(results))
    }
  }

  private def handleRecord(exchange: HttpExchange): Unit =
    BattleResultApiCodec.parseRecordCommand(HttpRouteSupport.readRequestBody(exchange)) match {
      case Right(command) =>
        service.record(command) match {
          case Right(record) =>
            HttpRouteSupport.sendJson(exchange, 201, BattleResultApiCodec.renderRecord(record))
          case Left(BattleResultRecordError.InvalidHandle) =>
            jsonError(exchange, 400, "invalid_handle", "invalid_handle")
          case Left(BattleResultRecordError.VisitorNotAllowed) =>
            jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
        }
      case Left(BattleResultRecordDecodeError.BadJson) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object.")
      case Left(BattleResultRecordDecodeError.InvalidBattleId) =>
        jsonError(exchange, 400, "invalid_battle_id", "invalid_battle_id")
      case Left(BattleResultRecordDecodeError.InvalidHandle) =>
        jsonError(exchange, 400, "invalid_handle", "invalid_handle")
      case Left(BattleResultRecordDecodeError.VisitorNotAllowed) =>
        jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
    }

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(
      exchange,
      status,
      BattleResultErrorResponse.render(message, code)
    )
}

object BattleResultRoutes {
  def apply(service: BattleResultService): BattleResultRoutes =
    new BattleResultRoutes(service)
}
