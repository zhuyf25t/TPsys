package slaydemo.backend.forum.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.forum.objects.apiTypes.ForumRequestFields
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object ForumRouteHttpSupport {
  def parseBody(exchange: HttpExchange): Either[String, ForumRequestFields] = {
    val body = HttpRouteSupport.readRequestBody(exchange)
    ForumRequestBodyParser.parse(body)
  }

  def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
