package slaydemo.backend.battle.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.api.BattleResultSubmissionRequest
import slaydemo.backend.battle.services.BattleResultService
import slaydemo.backend.shared.objects.{BattleId, UserId}

final class BattleResultRoutes(service: BattleResultService) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "POST" =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              val request = BattleResultSubmissionRequest(
                battleId = BattleId(fields.getOrElse("battleId", "")),
                handle = UserId(fields.getOrElse("handle", "")),
                displayName = fields.getOrElse("displayName", ""),
                finishedAt = fields.get("finishedAt").flatMap(_.toLongOption).getOrElse(0L),
                finishedAtLabel = fields.getOrElse("finishedAtLabel", ""),
                durationMs = fields.get("durationMs").flatMap(_.toLongOption).getOrElse(0L),
                score = fields.get("score").flatMap(_.toIntOption).getOrElse(0),
                placement = fields.get("placement").flatMap(parseNullableInt),
                aliveAtEnd = fields.get("aliveAtEnd").exists(_.equalsIgnoreCase("true")),
                ratingBefore = fields.get("ratingBefore").flatMap(_.toIntOption).getOrElse(0),
                ratingDelta = fields.get("ratingDelta").flatMap(_.toIntOption).getOrElse(0),
                ratingAfter = fields.get("ratingAfter").flatMap(_.toIntOption).getOrElse(0),
                resultLabel = fields.getOrElse("resultLabel", ""),
                modeLabel = fields.getOrElse("modeLabel", ""),
                mapLabel = fields.getOrElse("mapLabel", ""),
                highlightLine = fields.getOrElse("highlightLine", ""),
                playersLine = fields.getOrElse("playersLine", ""),
                timelineHint = fields.getOrElse("timelineHint", ""),
                currentLoadout = fields.get("currentLoadout").flatMap(v => if (v == "null" || v.isEmpty) None else Some(v))
              )

              service.record(request) match {
                case Right(record) =>
                  sendJson(exchange, 201, renderRecord(record))
                case Left("invalid_handle") =>
                  sendJson(exchange, 400, """{"error":"invalid_handle"}""")
                case Left("invalid_battle_id") =>
                  sendJson(exchange, 400, """{"error":"invalid_battle_id"}""")
                case Left(other) =>
                  sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
              }
            case Left(error) =>
              sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
          }
        case "GET" =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          val handle = query.get("handle")
          val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(25)
          val results = service.list(handle, limit)
          sendJson(exchange, 200, renderRecords(results))
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
      val stringPattern = "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
      val stringPairs = stringPattern.findAllMatchIn(body).map(matchResult => matchResult.group(1) -> unescape(matchResult.group(2))).toMap
      val fields = stringPairs ++ extractPrimitiveFields(body)
      if (fields.nonEmpty) Right(fields) else Left("Request body must be a JSON object.")
    }
  }

  private def extractPrimitiveFields(body: String): Map[String, String] = {
    val candidates = Seq(
      "finishedAt",
      "durationMs",
      "score",
      "placement",
      "aliveAtEnd",
      "ratingBefore",
      "ratingDelta",
      "ratingAfter"
    )

    candidates.flatMap { field =>
      val pattern = s""""$field"\\s*:\\s*([\\w-]+)""".r
      pattern.findFirstMatchIn(body).map(matchResult => field -> matchResult.group(1))
    }.toMap
  }

  private def parseQuery(query: String): Map[String, String] = {
    Option(query).toSeq
      .flatMap(_.split("&").toSeq)
      .flatMap { pair =>
        pair.split("=", 2).toSeq match {
          case Seq(key, value) => Some(urlDecode(key) -> urlDecode(value))
          case Seq(key)        => Some(urlDecode(key) -> "")
          case _                => None
        }
      }
      .toMap
  }

  private def parseNullableInt(value: String): Option[Int] = {
    if (value == "null" || value.isEmpty) None else value.toIntOption
  }

  private def renderRecord(record: slaydemo.backend.battle.objects.BattleResultRecord): String = {
    val currentLoadout = record.currentLoadout.map(value => s""""${escape(value)}"""").getOrElse("null")
    s"""{
       |  "battleId": "${escape(record.battleId.value)}",
       |  "handle": "${escape(record.handle.value)}",
       |  "displayName": "${escape(record.displayName)}",
       |  "finishedAt": ${record.finishedAt},
       |  "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |  "durationMs": ${record.durationMs},
       |  "score": ${record.score},
       |  "placement": ${record.placement.map(_.toString).getOrElse("null")},
       |  "aliveAtEnd": ${record.aliveAtEnd},
       |  "ratingBefore": ${record.ratingBefore},
       |  "ratingDelta": ${record.ratingDelta},
       |  "ratingAfter": ${record.ratingAfter},
       |  "resultLabel": "${escape(record.resultLabel)}",
       |  "modeLabel": "${escape(record.modeLabel)}",
       |  "mapLabel": "${escape(record.mapLabel)}",
       |  "highlightLine": "${escape(record.highlightLine)}",
       |  "playersLine": "${escape(record.playersLine)}",
       |  "timelineHint": "${escape(record.timelineHint)}",
       |  "currentLoadout": $currentLoadout
       |}""".stripMargin
  }

  private def renderRecords(records: Seq[slaydemo.backend.battle.objects.BattleResultRecord]): String = {
    val body = records.map(renderRecord).mkString(",\n")
    s"""{"results":[
       |$body
       |]}""".stripMargin
  }

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type")
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
