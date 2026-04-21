package slaydemo.backend.replay.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.replay.api.{ReplayCatalogView, ReplayDetailView, ReplaySubmissionRequest}
import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final class ReplayRoutes(service: ReplayService) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "POST" if isCatalogPath(exchange.getRequestURI.getPath) =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              val request = ReplaySubmissionRequest(
                replayId = ReplayId(fields.getOrElse("replayId", "")),
                battleId = BattleId(fields.getOrElse("battleId", "")),
                handle = UserId(fields.getOrElse("handle", "")),
                displayName = fields.getOrElse("displayName", ""),
                finishedAt = fields.get("finishedAt").flatMap(_.toLongOption).getOrElse(0L),
                finishedAtLabel = fields.getOrElse("finishedAtLabel", ""),
                title = fields.getOrElse("title", ""),
                modeLabel = fields.getOrElse("modeLabel", ""),
                resultLabel = fields.getOrElse("resultLabel", ""),
                mapLabel = fields.getOrElse("mapLabel", ""),
                highlightLine = fields.getOrElse("highlightLine", ""),
                coverLabel = fields.getOrElse("coverLabel", ""),
                playersLine = fields.getOrElse("playersLine", ""),
                timelineHint = fields.getOrElse("timelineHint", ""),
                score = fields.get("score").flatMap(_.toIntOption).getOrElse(0),
                placement = fields.get("placement").flatMap(parseNullableInt),
                durationMs = fields.get("durationMs").flatMap(_.toLongOption).getOrElse(0L),
                aliveAtEnd = fields.get("aliveAtEnd").exists(_.equalsIgnoreCase("true")),
                thumbnailDataUrl = fields.get("thumbnailDataUrl").flatMap(v => if (v == "null" || v.isEmpty) None else Some(v)),
                currentLoadout = fields.get("currentLoadout").flatMap(v => if (v == "null" || v.isEmpty) None else Some(v)),
                frameCount = fields.get("frameCount").flatMap(_.toIntOption).getOrElse(0),
                playbackAvailable = fields.get("playbackAvailable").exists(_.equalsIgnoreCase("true")),
                framesJson = fields.getOrElse("framesJson", "[]")
              )

              service.record(request) match {
                case Right(record) =>
                  val detail = service.load(record.replayId).getOrElse(toDetailView(record))
                  sendJson(exchange, 201, renderDetail(detail))
                case Left("invalid_replay_id") =>
                  sendJson(exchange, 400, """{"error":"invalid_replay_id"}""")
                case Left("invalid_battle_id") =>
                  sendJson(exchange, 400, """{"error":"invalid_battle_id"}""")
                case Left("invalid_handle") =>
                  sendJson(exchange, 400, """{"error":"invalid_handle"}""")
                case Left(other) =>
                  sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
              }
            case Left(error) =>
              sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
          }
        case "GET" if isCatalogPath(exchange.getRequestURI.getPath) =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(25)
          sendJson(exchange, 200, renderCatalog(service.list(limit)))
        case "GET" =>
          loadDetail(exchange) match {
            case Some(replayId) =>
              service.load(replayId) match {
                case Some(detail) =>
                  sendJson(exchange, 200, renderDetail(detail))
                case None =>
                  sendJson(exchange, 404, """{"error":"replay_not_found"}""")
              }
            case None =>
              sendJson(exchange, 404, """{"error":"replay_not_found"}""")
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

  private def isCatalogPath(path: String): Boolean = {
    path == "/replay/catalog" || path == "/replay/catalog/"
  }

  private def loadDetail(exchange: HttpExchange): Option[ReplayId] = {
    val path = exchange.getRequestURI.getPath.stripSuffix("/")
    val prefix = "/replay/catalog/"
    if (path.startsWith(prefix) && path.length > prefix.length) {
      Some(ReplayId(path.drop(prefix.length)))
    } else {
      None
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
      "score",
      "placement",
      "durationMs",
      "aliveAtEnd",
      "frameCount",
      "playbackAvailable"
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
          case _               => None
        }
      }
      .toMap
  }

  private def parseNullableInt(value: String): Option[Int] = {
    if (value == "null" || value.isEmpty) None else value.toIntOption
  }

  private def renderCatalog(records: Seq[ReplayCatalogView]): String = {
    val body = records.map(renderCatalogEntry).mkString(",\n")
    s"""{"replays":[
       |$body
       |]}""".stripMargin
  }

  private def renderCatalogEntry(record: ReplayCatalogView): String = {
    s"""  {
       |    "id": "${escape(record.replayId.value)}",
       |    "battleId": "${escape(record.battleId.value)}",
       |    "title": "${escape(record.title)}",
       |    "modeLabel": "${escape(record.modeLabel)}",
       |    "resultLabel": "${escape(record.resultLabel)}",
       |    "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |    "mapLabel": "${escape(record.mapLabel)}",
       |    "highlightLine": "${escape(record.highlightLine)}",
       |    "coverLabel": "${escape(record.coverLabel)}",
       |    "playersLine": "${escape(record.playersLine)}",
       |    "timelineHint": "${escape(record.timelineHint)}",
       |    "score": ${record.score},
       |    "placement": ${record.placement.map(_.toString).getOrElse("null")},
       |    "durationMs": ${record.durationMs},
       |    "aliveAtEnd": ${record.aliveAtEnd},
       |    "thumbnailDataUrl": ${record.thumbnailDataUrl.map(value => s"\"${escape(value)}\"").getOrElse("null")},
       |    "frameCount": ${record.frameCount},
       |    "playbackAvailable": ${record.playbackAvailable}
       |  }""".stripMargin
  }

  private def renderDetail(record: ReplayDetailView): String = {
    s"""{
       |  "replay": {
       |    "id": "${escape(record.replayId.value)}",
       |    "battleId": "${escape(record.battleId.value)}",
       |    "handle": "${escape(record.handle.value)}",
       |    "displayName": "${escape(record.displayName)}",
       |    "finishedAt": ${record.finishedAt},
       |    "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |    "title": "${escape(record.title)}",
       |    "modeLabel": "${escape(record.modeLabel)}",
       |    "resultLabel": "${escape(record.resultLabel)}",
       |    "mapLabel": "${escape(record.mapLabel)}",
       |    "highlightLine": "${escape(record.highlightLine)}",
       |    "coverLabel": "${escape(record.coverLabel)}",
       |    "playersLine": "${escape(record.playersLine)}",
       |    "timelineHint": "${escape(record.timelineHint)}",
       |    "score": ${record.score},
       |    "placement": ${record.placement.map(_.toString).getOrElse("null")},
       |    "durationMs": ${record.durationMs},
       |    "aliveAtEnd": ${record.aliveAtEnd},
       |    "thumbnailDataUrl": ${record.thumbnailDataUrl.map(value => s"\"${escape(value)}\"").getOrElse("null")},
       |    "currentLoadout": ${record.currentLoadout.map(value => s"\"${escape(value)}\"").getOrElse("null")},
       |    "frameCount": ${record.frameCount},
       |    "playbackAvailable": ${record.playbackAvailable},
       |    "frames": ${record.framesJson}
       |  }
       |}""".stripMargin
  }

  private def toDetailView(record: ReplayRecord): ReplayDetailView = {
    ReplayDetailView(
      replayId = record.replayId,
      battleId = record.battleId,
      handle = record.handle,
      displayName = record.displayName,
      finishedAt = record.finishedAt,
      finishedAtLabel = record.finishedAtLabel,
      title = record.title,
      modeLabel = record.modeLabel,
      resultLabel = record.resultLabel,
      mapLabel = record.mapLabel,
      highlightLine = record.highlightLine,
      coverLabel = record.coverLabel,
      playersLine = record.playersLine,
      timelineHint = record.timelineHint,
      score = record.score,
      placement = record.placement,
      durationMs = record.durationMs,
      aliveAtEnd = record.aliveAtEnd,
      thumbnailDataUrl = record.thumbnailDataUrl,
      currentLoadout = record.currentLoadout,
      frameCount = record.frameCount,
      playbackAvailable = record.playbackAvailable,
      framesJson = "[]"
    )
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
