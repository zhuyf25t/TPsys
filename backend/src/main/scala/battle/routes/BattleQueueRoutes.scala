package slaydemo.backend.battle.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.api.BattleQueueJoinRequest
import slaydemo.backend.battle.objects.{BattleQueuePlayer, BattleQueueSnapshot}
import slaydemo.backend.battle.services.BattleQueueService

final class BattleQueueRoutes(service: BattleQueueService) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      val path = exchange.getRequestURI.getPath.stripSuffix("/")
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "POST" if path == "/battle/queue/join" =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              service.join(BattleQueueJoinRequest(fields.getOrElse("handle", ""))) match {
                case Right(snapshot) =>
                  sendJson(exchange, 200, renderSnapshot(snapshot))
                case Left("invalid_handle") =>
                  sendJson(exchange, 400, """{"error":"invalid_handle"}""")
                case Left(other) =>
                  sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
              }
            case Left(error) =>
              sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
          }
        case "GET" if path == "/battle/queue/status" =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          service.status(query.getOrElse("ticket", "")) match {
            case Some(snapshot) =>
              sendJson(exchange, 200, renderSnapshot(snapshot))
            case None =>
              sendJson(exchange, 404, """{"error":"ticket_not_found"}""")
          }
        case "POST" if path == "/battle/queue/leave" =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              val left = service.leave(fields.getOrElse("ticket", fields.getOrElse("ticketId", "")))
              sendJson(exchange, 200, s"""{"left":$left}""")
            case Left(error) =>
              sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
          }
        case "HEAD" =>
          exchange.sendResponseHeaders(200, -1)
        case _ =>
          sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
      }
    } finally {
      exchange.close()
    }
  }

  private def parseBody(input: InputStream): Either[String, Map[String, String]] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim
    if (body.isEmpty) {
      Right(Map.empty)
    } else {
      val pattern = "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
      val pairs = pattern.findAllMatchIn(body).map(matchResult => matchResult.group(1) -> unescape(matchResult.group(2))).toMap
      if (pairs.nonEmpty) Right(pairs) else Left("Request body must be a JSON object.")
    }
  }

  private def parseQuery(query: String): Map[String, String] = {
    Option(query).toSeq
      .flatMap(_.split("&").toSeq)
      .flatMap { pair =>
        pair.split("=", 2).toSeq match {
          case Seq(key, value) => Some(urlDecode(key) -> urlDecode(value))
          case Seq(key)        => Some(urlDecode(key) -> "")
          case _               => None
        }
      }
      .toMap
  }

  private def renderSnapshot(snapshot: BattleQueueSnapshot): String = {
    val players = snapshot.players.map(renderPlayer).mkString(",")
    s"""{
       |  "ticketId": "${escape(snapshot.ticketId)}",
       |  "matchId": "${escape(snapshot.matchId)}",
       |  "startsAt": ${snapshot.startsAt},
       |  "players": [$players],
       |  "capacity": ${snapshot.capacity},
       |  "durationMs": ${snapshot.durationMs}
       |}""".stripMargin
  }

  private def renderPlayer(player: BattleQueuePlayer): String =
    s"""{"handle":"${escape(player.handle)}","joinedAt":${player.joinedAt}}"""

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token")
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  private def sendJson(exchange: HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }

  private def urlDecode(value: String): String =
    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")
}

