package slaydemo.backend.bots.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.shared.routes.HttpRouteSupport

final class BotProfileRoutes(service: BotProfileService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" =>
          HttpRouteSupport.sendJson(exchange, 200, BotProfileRouteJsonRenderer.renderProfiles(service.list()))
        case _ =>
          HttpRouteSupport.sendJsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }
}

object BotProfileRoutes {
  def apply(service: BotProfileService): BotProfileRoutes =
    new BotProfileRoutes(service)
}
