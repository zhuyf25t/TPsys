package slaydemo.backend.shared.api

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.shared.routes.HttpRouteSupport

trait BackendAPIMessage

final case class BackendAPIRequest(
  method: String,
  path: String,
  rawQuery: String,
  query: Map[String, String],
  body: String
)

sealed trait BackendAPIResponse {
  def write(exchange: HttpExchange): Unit
}

object BackendAPIResponse {
  def empty(status: Int): BackendAPIResponse =
    new BackendAPIResponse {
      override def write(exchange: HttpExchange): Unit =
        HttpRouteSupport.sendEmpty(exchange, status)
    }

  def json(status: Int, body: String): BackendAPIResponse =
    new BackendAPIResponse {
      override def write(exchange: HttpExchange): Unit =
        HttpRouteSupport.sendJson(exchange, status, body)
    }

  def jsonError(status: Int, code: String, message: String): BackendAPIResponse =
    new BackendAPIResponse {
      override def write(exchange: HttpExchange): Unit =
        HttpRouteSupport.sendJsonError(exchange, status, code, message)
    }

  def stream(writeResponse: HttpExchange => Unit): BackendAPIResponse =
    new BackendAPIResponse {
      override def write(exchange: HttpExchange): Unit =
        writeResponse(exchange)
    }
}

trait BackendAPIMessagePlanner[-M <: BackendAPIMessage] {
  def plan(message: M): BackendIO[BackendAPIResponse]
}

trait BackendAPIEndpoint {
  def messageKey: String
  def plan(request: BackendAPIRequest): BackendIO[BackendAPIResponse]
}

object BackendAPIEndpoint {
  def apply[M <: BackendAPIMessage](
    key: String,
    decode: BackendAPIRequest => M,
    planner: BackendAPIMessagePlanner[M]
  ): BackendAPIEndpoint =
    new BackendAPIEndpoint {
      override val messageKey: String = key

      override def plan(request: BackendAPIRequest): BackendIO[BackendAPIResponse] =
        planner.plan(decode(request))
    }
}

object BackendAPIExchangeRouter {
  def handle(endpoint: BackendAPIEndpoint)(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)
    try {
      val request = BackendAPIRequest.from(exchange)
      endpoint.plan(request).unsafeRun().write(exchange)
    } finally {
      exchange.close()
    }
  }
}

object BackendAPIRequest {
  def from(exchange: HttpExchange): BackendAPIRequest = {
    val uri = exchange.getRequestURI
    val rawQuery = Option(uri.getRawQuery).getOrElse("")
    BackendAPIRequest(
      method = exchange.getRequestMethod.toUpperCase(Locale.ROOT),
      path = uri.getPath,
      rawQuery = rawQuery,
      query = queryParams(rawQuery),
      body = HttpRouteSupport.readRequestBody(exchange)
    )
  }

  private def queryParams(rawQuery: String): Map[String, String] =
    Option(rawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}
