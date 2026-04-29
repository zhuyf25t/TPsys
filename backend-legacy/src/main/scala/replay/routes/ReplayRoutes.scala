package slaydemo.backend.replay.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.services.BattleResultService
import slaydemo.backend.replay.api.{ReplayCatalogView, ReplayCommentSubmissionRequest, ReplayCommentView, ReplayDetailView, ReplaySubmissionRequest}
import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.replay.support.ReplayJsonSupport
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final class ReplayRoutes(service: ReplayService, battleResultService: Option[BattleResultService] = None) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      val target = parseCatalogTarget(exchange.getRequestURI.getPath)
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "POST" =>
          target match {
            case CatalogCollection =>
              handleCatalogSubmission(exchange)
            case ReplayCommentsTarget(replayId) =>
              withExistingReplay(exchange, replayId) {
                parseBody(exchange.getRequestBody) match {
                  case Right(fields) =>
                    val request = ReplayCommentSubmissionRequest(
                      replayId = replayId,
                      authorHandle = UserId(fields.getOrElse("authorHandle", "")),
                      body = fields.getOrElse("body", "")
                    )

                    service.addComment(request) match {
                      case Right(comment) =>
                        sendJson(exchange, 201, renderComment(comment))
                      case Left("invalid_replay_id") =>
                        sendJson(exchange, 400, """{"error":"invalid_replay_id"}""")
                      case Left("replay_not_found") =>
                        sendJson(exchange, 404, """{"error":"replay_not_found"}""")
                      case Left("invalid_author_handle") =>
                        sendJson(exchange, 400, """{"error":"invalid_author_handle"}""")
                      case Left("visitor_not_allowed") =>
                        sendJson(exchange, 403, """{"error":"visitor_not_allowed"}""")
                      case Left("invalid_body") =>
                        sendJson(exchange, 400, """{"error":"invalid_body"}""")
                      case Left(other) =>
                        sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
                    }
                  case Left(error) =>
                    sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
                }
              }
            case InvalidCatalogPath =>
              sendJson(exchange, 404, """{"error":"replay_not_found"}""")
            case _ =>
              sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
          }
        case "GET" =>
          target match {
            case CatalogCollection =>
              val query = parseQuery(exchange.getRequestURI.getRawQuery)
              val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(25)
              sendJson(exchange, 200, renderCatalog(service.list(limit)))
            case ReplayDetailTarget(replayId) =>
              service.load(replayId) match {
                case Some(detail) =>
                  sendJson(exchange, 200, renderDetail(detail))
                case None =>
                  sendJson(exchange, 404, """{"error":"replay_not_found"}""")
              }
            case ReplayCommentsTarget(replayId) =>
              withExistingReplay(exchange, replayId) {
                val query = parseQuery(exchange.getRequestURI.getRawQuery)
                val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(50)
                sendJson(exchange, 200, renderComments(service.listComments(replayId, limit)))
              }
            case InvalidCatalogPath =>
              sendJson(exchange, 404, """{"error":"replay_not_found"}""")
          }
        case "HEAD" =>
          target match {
            case InvalidCatalogPath =>
              exchange.sendResponseHeaders(404, -1)
            case _ =>
              exchange.sendResponseHeaders(200, -1)
          }
        case _ =>
          if (target == InvalidCatalogPath) {
            sendJson(exchange, 404, """{"error":"replay_not_found"}""")
          } else {
            sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
          }
      }
    } catch {
      case error: Throwable =>
        Console.err.println(
          s"[replay] unhandled route failure ${exchange.getRequestMethod} ${exchange.getRequestURI.getPath}: ${error.getMessage}"
        )
        error.printStackTrace(Console.err)
        try sendJson(exchange, 500, """{"error":"internal_server_error"}""")
        catch {
          case _: Throwable =>
        }
    } finally {
      exchange.close()
    }
  }

  private def handleCatalogSubmission(exchange: HttpExchange): Unit = {
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
          case Left("visitor_not_allowed") =>
            sendJson(exchange, 403, """{"error":"visitor_not_allowed"}""")
          case Left(other) =>
            sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
        }
      case Left(error) =>
        sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
    }
  }

  private def withExistingReplay(exchange: HttpExchange, replayId: ReplayId)(action: => Unit): Unit = {
    service.load(replayId) match {
      case Some(_) => action
      case None    => sendJson(exchange, 404, """{"error":"replay_not_found"}""")
    }
  }

  private def parseCatalogTarget(path: String): CatalogTarget = {
    val normalizedPath = Option(path).getOrElse("").trim.stripPrefix("/").stripSuffix("/")
    if (normalizedPath.isEmpty) {
      return InvalidCatalogPath
    }

    val segments = normalizedPath.split("/", -1).toList
    if (segments.exists(_.isEmpty)) {
      return InvalidCatalogPath
    }

    segments match {
      case List("replay", "catalog") =>
        CatalogCollection
      case List("replay", "catalog", replayId) if isSafePathIdentifier(replayId) =>
        ReplayDetailTarget(ReplayId(replayId))
      case List("replay", "catalog", replayId, "comments") if isSafePathIdentifier(replayId) =>
        ReplayCommentsTarget(ReplayId(replayId))
      case _ =>
        InvalidCatalogPath
    }
  }

  private def isSafePathIdentifier(value: String): Boolean = {
    value.nonEmpty && value.forall(ch => ch.isLetterOrDigit || ch == '-' || ch == '_' || ch == '.' || ch == '~')
  }

  private sealed trait CatalogTarget
  private case object CatalogCollection extends CatalogTarget
  private final case class ReplayDetailTarget(replayId: ReplayId) extends CatalogTarget
  private final case class ReplayCommentsTarget(replayId: ReplayId) extends CatalogTarget
  private case object InvalidCatalogPath extends CatalogTarget

  private def parseBody(input: InputStream): Either[String, Map[String, String]] = {
    ReplayJsonSupport.parseFlatObject(input)
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
       |    "replayId": "${escape(record.replayId.value)}",
       |    "battleId": "${escape(record.battleId.value)}",
       |    "title": "${escape(record.title)}",
       |    "modeLabel": "${escape(record.modeLabel)}",
       |    "resultLabel": "${escape(record.resultLabel)}",
       |    "finishedAt": ${record.finishedAt},
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
       |    "playbackAvailable": ${record.playbackAvailable},
       |    ${renderRatingFields(record.battleId.value, record.handle.value)}
       |  }""".stripMargin
  }

  private def renderDetail(record: ReplayDetailView): String = {
    s"""{
       |  "replay": {
       |    "replayId": "${escape(record.replayId.value)}",
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
       |    ${renderRatingFields(record.battleId.value, record.handle.value)},
       |    "frames": ${record.framesJson}
       |  }
       |}""".stripMargin
  }

  private def renderRatingFields(battleId: String, handle: String): String = {
    val rating = battleResultService
      .flatMap(_.list(Some(handle), Some(battleId), 1).headOption)
      .map(record => ReplayRatingView(Some(record.ratingBefore), Some(record.ratingDelta), Some(record.ratingAfter)))
      .getOrElse(ReplayRatingView(None, None, None))

    s""""ratingBefore": ${renderNullableInt(rating.ratingBefore)},
       |    "ratingDelta": ${renderNullableInt(rating.ratingDelta)},
       |    "ratingAfter": ${renderNullableInt(rating.ratingAfter)}""".stripMargin
  }

  private def renderNullableInt(value: Option[Int]): String = value.map(_.toString).getOrElse("null")

  private final case class ReplayRatingView(
    ratingBefore: Option[Int],
    ratingDelta: Option[Int],
    ratingAfter: Option[Int]
  )

  private def renderComments(records: Seq[ReplayCommentView]): String = {
    val body = records.map(renderCommentEntry).mkString(",\n")
    s"""{"comments":[
       |$body
       |]}""".stripMargin
  }

  private def renderComment(record: ReplayCommentView): String = {
    s"""{
       |  "comment": {
       |    ${renderCommentFields(record)}
       |  }
       |}""".stripMargin
  }

  private def renderCommentEntry(record: ReplayCommentView): String = {
    s"""  {
       |    ${renderCommentFields(record)}
       |  }""".stripMargin
  }

  private def renderCommentFields(record: ReplayCommentView): String = {
    s""""id": "${escape(record.id)}",
       |    "replayId": "${escape(record.replayId.value)}",
       |    "authorHandle": "${escape(record.authorHandle.value)}",
       |    "body": "${escape(record.body)}",
       |    "createdAt": ${record.createdAt}""".stripMargin
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
      frameCount = 0,
      playbackAvailable = false,
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
    Option(value).getOrElse("")
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

}
