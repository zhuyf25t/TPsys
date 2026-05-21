package slaydemo.backend.shared.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.shared.api.{HealthErrorResponse, HealthJsonCodec}
import slaydemo.backend.shared.services.HealthService

final class HealthRoutes(service: HealthService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          HttpRouteSupport.sendJson(exchange, 200, HealthJsonCodec.render(service.current))
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case _ =>
          HttpRouteSupport.sendJson(exchange, 405, HealthJsonCodec.renderError(HealthErrorResponse.MethodNotAllowed))
      }
    } finally {
      exchange.close()
    }
  }
}

object HealthRoutes {
  def apply(service: HealthService): HealthRoutes =
    new HealthRoutes(service)
}
