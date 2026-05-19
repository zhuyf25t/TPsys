package slaydemo.backend.shared.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.shared.api.{BackendAPIEndpoint, HealthResponse, HealthStatus}
import slaydemo.backend.shared.services.HealthService
import slaydemo.backend.shared.storage.StorageMode

final class HealthRoutes(service: HealthService) {
  def apiEndpoints: Vector[BackendAPIEndpoint] =
    Vector(HealthAPIMessagePlanner.endpoint(service))

  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          HttpRouteSupport.sendJson(exchange, 200, HealthRouteJsonRenderer.render(service.current))
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case _ =>
          HttpRouteSupport.sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
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

private[routes] object HealthRouteJsonRenderer {
  def render(response: HealthResponse): String =
    s"""{"status":"${HealthStatus.wireValue(response.status)}","service":"${HttpRouteSupport.escapeJson(response.service.value)}","port":${response.port.value},"storageMode":"${StorageMode.wireValue(response.storageMode)}"}"""
}
