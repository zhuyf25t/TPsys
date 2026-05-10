package slaydemo.backend.battle.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object BattleRouteHttpSupport {
  def handlePost(exchange: HttpExchange)(action: => Unit): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "POST" =>
          action
        case _ =>
          jsonError(exchange, BattleRouteErrorMapper.unsupportedPost)
      }
    } finally {
      exchange.close()
    }
  }

  def handleGet(exchange: HttpExchange)(action: => Unit): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          action
        case _ =>
          jsonError(exchange, BattleRouteErrorMapper.unsupportedGet)
      }
    } finally {
      exchange.close()
    }
  }

  def readJsonObject(exchange: HttpExchange): Either[String, Map[String, BattleJsonValue]] =
    BattleJsonObjectParser
      .parse(HttpRouteSupport.readRequestBody(exchange))
      .left
      .map(_ => "Request body must be a JSON object with supported primitive or object fields.")

  def jsonError(exchange: HttpExchange, error: BattleRouteError): Unit =
    jsonError(exchange, error.status, error.code, error.message)

  def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJsonError(exchange, status, code, message)
}
