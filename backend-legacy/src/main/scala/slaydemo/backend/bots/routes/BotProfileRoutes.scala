package slaydemo.backend.bots.routes

import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.bots.api.BotProfileView
import slaydemo.backend.bots.services.BotProfileService

final class BotProfileRoutes(service: BotProfileService) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" =>
          sendJson(exchange, 200, renderProfiles(service.list()))
        case "HEAD" =>
          exchange.sendResponseHeaders(200, -1)
        case _ =>
          sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
      }
    } finally {
      exchange.close()
    }
  }

  private def renderProfiles(profiles: Seq[BotProfileView]): String = {
    val body = profiles.map(renderProfile).mkString(",\n")
    s"""{"profiles":[
       |$body
       |]}""".stripMargin
  }

  private def renderProfile(profile: BotProfileView): String = {
    s"""  {
       |    "botId": "${escape(profile.botId)}",
       |    "handle": "${escape(profile.handle)}",
       |    "displayName": "${escape(profile.displayName)}",
       |    "initialRating": ${profile.initialRating},
       |    "profileTone": "${escape(profile.profileTone)}",
       |    "strategyLabel": "${escape(profile.strategyLabel)}",
       |    "skin": {
       |      "avatarKey": "${escape(profile.skin.avatarKey)}",
       |      "textureKey": "${escape(profile.skin.textureKey)}",
       |      "label": "${escape(profile.skin.label)}"
       |    }
       |  }""".stripMargin
  }

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type")
    headers.set("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  private def sendJson(exchange: HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
