package slaydemo.backend.shared.routes

import java.nio.charset.StandardCharsets

import com.sun.net.httpserver.HttpExchange

object HttpRouteSupport {
  def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token")
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  def sendEmpty(exchange: HttpExchange, status: Int): Unit =
    exchange.sendResponseHeaders(status, -1)

  def sendJson(exchange: HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }

  def sendJsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

  def readRequestBody(exchange: HttpExchange): String =
    String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)

  def escapeJson(value: String): String =
    value.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case char if char.isControl => f"\\u${char.toInt}%04x"
      case char => char.toString
    }

  private def jsonString(value: String): String =
    s""""${escapeJson(value)}""""
}
