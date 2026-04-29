package slaydemo.backend.battle.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.api.{BattleQueueJoinRequest, RealtimeRoomHeartbeatRequest}
import slaydemo.backend.battle.objects.{
  BattleQueueParticipant,
  BattleQueueSnapshot,
  BattleSessionBootstrap,
  BattleSessionBootstrapSeat,
  BattleSessionDescriptor,
  BattleSessionRosterEntry,
  RealtimeRoomSnapshot
}
import slaydemo.backend.battle.services.BattleQueueService
import slaydemo.backend.battle.rules.BattleRules
import slaydemo.backend.identity.services.IdentityService

final class BattleQueueRoutes(service: BattleQueueService, identityService: IdentityService) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      val path = exchange.getRequestURI.getPath.stripSuffix("/")
      val roomSnapshotPathRoomId = roomIdFromPath(path, "snapshot")
      val roomHeartbeatPathRoomId = roomIdFromPath(path, "heartbeat")
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" if roomSnapshotPathRoomId.nonEmpty =>
          handleRoomSnapshot(exchange, roomSnapshotPathRoomId)
        case "GET" if path == "/battle/rooms/snapshot" =>
          handleRoomSnapshot(exchange, None)
        case "POST" if roomHeartbeatPathRoomId.nonEmpty =>
          handleRoomHeartbeat(exchange, roomHeartbeatPathRoomId)
        case "POST" if path == "/battle/rooms/heartbeat" =>
          handleRoomHeartbeat(exchange, None)
        case "POST" if path == "/battle/queue/join" =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              val request = BattleQueueJoinRequest(
                handle = fields.getOrElse("handle", ""),
                sessionToken = fields.get("sessionToken"),
                queueRequestId = fields.get("queueRequestId"),
                rating = fields.get("rating").flatMap(_.toIntOption),
                avatar = fields.get("avatar"),
                skin = fields.get("skin")
              )

              validateBattleIdentity(request) match {
                case Left("visitor_not_allowed") =>
                  sendJson(exchange, 403, """{"error":"visitor_not_allowed"}""")
                case Left(error) =>
                  sendJson(exchange, 401, s"""{"error":"${escape(error)}"}""")
                case Right(_) =>
                  service.join(request) match {
                    case Right(snapshot) =>
                      sendJson(exchange, 200, renderSnapshot(snapshot))
                    case Left("invalid_handle") =>
                      sendJson(exchange, 400, """{"error":"invalid_handle"}""")
                    case Left("auth_required") =>
                      sendJson(exchange, 401, """{"error":"auth_required"}""")
                    case Left("visitor_not_allowed") =>
                      sendJson(exchange, 403, """{"error":"visitor_not_allowed"}""")
                    case Left(other) =>
                      sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
                  }
              }
            case Left(error) =>
              sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
          }
        case "GET" if path == "/battle/queue/status" =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          service.status(query.getOrElse("ticketId", "")) match {
            case Some(snapshot) =>
              sendJson(exchange, 200, renderSnapshot(snapshot))
            case None =>
              sendJson(exchange, 404, """{"error":"ticket_not_found"}""")
          }
        case "POST" if path == "/battle/queue/leave" =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              val left = service.leave(fields.getOrElse("ticketId", ""))
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

  private def validateBattleIdentity(request: BattleQueueJoinRequest): Either[String, Unit] = {
    val handle = request.handle.trim
    val sessionToken = request.sessionToken.map(_.trim).filter(_.nonEmpty)
    if (handle.isEmpty || sessionToken.isEmpty) {
      Left("auth_required")
    } else if (isVisitorHandle(handle)) {
      Left("visitor_not_allowed")
    } else {
      identityService.loadAccountBySessionToken(sessionToken.get) match {
        case Some(account) if !isVisitorHandle(account.handle) && account.handle.trim.equalsIgnoreCase(handle) =>
          Right(())
        case Some(account) if isVisitorHandle(account.handle) =>
          Left("visitor_not_allowed")
        case _ =>
          Left("auth_required")
      }
    }
  }

  private def isVisitorHandle(handle: String): Boolean =
    BattleRules.isVisitorHandle(handle)

  private def handleRoomSnapshot(exchange: HttpExchange, pathRoomId: Option[String]): Unit = {
    val query = parseQuery(exchange.getRequestURI.getRawQuery)
    pathRoomId.orElse(query.get("roomId")).map(_.trim).filter(_.nonEmpty) match {
      case Some(roomId) =>
        service.roomSnapshot(roomId) match {
          case Some(snapshot) =>
            sendJson(exchange, 200, renderRealtimeRoomSnapshot(snapshot))
          case None =>
            sendJson(exchange, 404, """{"error":"room_not_found"}""")
        }
      case None =>
        sendJson(exchange, 400, """{"error":"invalid_room_id"}""")
    }
  }

  private def handleRoomHeartbeat(exchange: HttpExchange, pathRoomId: Option[String]): Unit = {
    val query = parseQuery(exchange.getRequestURI.getRawQuery)
    parseBody(exchange.getRequestBody) match {
      case Right(fields) =>
        val request = RealtimeRoomHeartbeatRequest(
          ticketId = fields.get("ticketId").orElse(query.get("ticketId")),
          handle = fields.get("handle").orElse(query.get("handle"))
        )
        val roomId = pathRoomId.orElse(fields.get("roomId")).orElse(query.get("roomId")).map(_.trim).filter(_.nonEmpty)
        roomId match {
          case Some(value) =>
            service.heartbeat(value, request.ticketId, request.handle) match {
              case Some(snapshot) =>
                sendJson(exchange, 200, renderRealtimeRoomSnapshot(snapshot))
              case None =>
                sendJson(exchange, 404, """{"error":"room_not_found"}""")
            }
          case None =>
            sendJson(exchange, 400, """{"error":"invalid_room_id"}""")
        }
      case Left(error) =>
        sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
    }
  }

  private def roomIdFromPath(path: String, action: String): Option[String] = {
    val prefix = "/battle/rooms/"
    val suffix = s"/$action"
    if (path.startsWith(prefix) && path.endsWith(suffix) && path.length > prefix.length + suffix.length) {
      val rawRoomId = path.substring(prefix.length, path.length - suffix.length)
      Some(urlDecode(rawRoomId)).map(_.trim).filter(_.nonEmpty)
    } else {
      None
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
    val participants = snapshot.participants.map(renderParticipant).mkString(",")
    val battleSession = renderBattleSession(snapshot.battleSession)
    s"""{
       |  "ticketId": "${escape(snapshot.ticketId)}",
       |  "playerId": "${escape(snapshot.playerId)}",
       |  "roomId": "${escape(snapshot.roomId)}",
       |  "createdAt": ${snapshot.createdAt},
       |  "startsAt": ${snapshot.startsAt},
       |  "deadline": ${snapshot.deadline},
       |  "participants": [$participants],
       |  "capacity": ${snapshot.capacity},
       |  "durationMs": ${snapshot.durationMs},
       |  "phase": "${escape(snapshot.phase)}",
       |  "finishedAt": ${renderOptionalLong(snapshot.finishedAt)},
       |  "battleSession": $battleSession
       |}""".stripMargin
  }

  private def renderRealtimeRoomSnapshot(snapshot: RealtimeRoomSnapshot): String = {
    val participants = snapshot.participants.map(renderParticipant).mkString(",")
    val battleSession = renderBattleSession(snapshot.battleSession)
    s"""{
       |  "roomId": "${escape(snapshot.roomId)}",
       |  "serverTime": ${snapshot.serverTime},
       |  "participants": [$participants],
       |  "capacity": ${snapshot.capacity},
       |  "phase": "${escape(snapshot.phase)}",
       |  "finishedAt": ${renderOptionalLong(snapshot.finishedAt)},
       |  "battleSession": $battleSession
       |}""".stripMargin
  }

  private def renderOptionalLong(value: Option[Long]): String =
    value.map(_.toString).getOrElse("null")

  private def renderBattleSession(battleSession: Option[BattleSessionDescriptor]): String =
    battleSession match {
      case Some(descriptor) =>
        val roster = descriptor.roster.map(renderBattleRosterEntry).mkString(",")
        val bootstrap = renderBattleBootstrap(descriptor.bootstrap)
        s"""{
           |  "battleId": "${escape(descriptor.battleId)}",
           |  "startedAt": ${descriptor.startedAt},
           |  "serverTime": ${descriptor.serverTime},
           |  "roster": [$roster],
           |  "capacity": ${descriptor.capacity},
           |  "bootstrap": $bootstrap
           |}""".stripMargin
      case None =>
        "null"
    }

  private def renderBattleBootstrap(bootstrap: BattleSessionBootstrap): String = {
    val seats = bootstrap.seats.map(renderBattleBootstrapSeat).mkString(",")
    s"""{"seats":[$seats]}"""
  }

  private def renderBattleRosterEntry(entry: BattleSessionRosterEntry): String = {
    val requiredFields = Vector(
      s""""seat":${entry.seat}""",
      s""""playerId":"${escape(entry.playerId)}"""",
      s""""handle":"${escape(entry.handle)}"""",
      s""""joinedAt":${entry.joinedAt}"""
    )
    val optionalFields = Vector(
      entry.rating.map(rating => s""""rating":$rating"""),
      entry.avatar.map(avatar => s""""avatar":"${escape(avatar)}""""),
      entry.skin.map(skin => s""""skin":"${escape(skin)}"""")
    ).flatten

    (requiredFields ++ optionalFields).mkString("{", ",", "}")
  }

  private def renderBattleBootstrapSeat(entry: BattleSessionBootstrapSeat): String = {
    val requiredFields = Vector(
      s""""seat":${entry.seat}""",
      s""""playerId":"${escape(entry.playerId)}"""",
      s""""heroId":"${escape(entry.heroId)}"""",
      s""""handle":"${escape(entry.handle)}"""",
      s""""displayName":"${escape(entry.displayName)}"""",
      s""""joinedAt":${entry.joinedAt}""",
      s""""isBot":${entry.isBot}""",
      s""""spawnPointIndex":${entry.spawnPointIndex}"""
    )
    val optionalFields = Vector(
      entry.rating.map(rating => s""""rating":$rating"""),
      entry.avatar.map(avatar => s""""avatar":"${escape(avatar)}""""),
      entry.skin.map(skin => s""""skin":"${escape(skin)}"""")
    ).flatten

    (requiredFields ++ optionalFields).mkString("{", ",", "}")
  }

  private def renderParticipant(participant: BattleQueueParticipant): String = {
    val requiredFields = Vector(
      s""""playerId":"${escape(participant.playerId)}"""",
      s""""handle":"${escape(participant.handle)}"""",
      s""""joinedAt":${participant.joinedAt}""",
      s""""lastSeen":${participant.lastSeen}"""
    )
    val optionalFields = Vector(
      participant.rating.map(rating => s""""rating":$rating"""),
      participant.avatar.map(avatar => s""""avatar":"${escape(avatar)}""""),
      participant.skin.map(skin => s""""skin":"${escape(skin)}"""")
    ).flatten

    (requiredFields ++ optionalFields).mkString("{", ",", "}")
  }

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
