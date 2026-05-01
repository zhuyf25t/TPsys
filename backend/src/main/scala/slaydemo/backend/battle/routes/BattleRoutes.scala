package slaydemo.backend.battle.routes

import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.api.{
  BattleCommandRequest,
  BattleCommandVector,
  BattleQueueLeaveRequest,
  RealtimeRoomHeartbeatRequest
}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{
  BattleQueueJoinCommand,
  BattleCommandSubmitError,
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueLeaveOutcome,
  BattleQueueService,
  BattleQueueStatusError,
  BattleRoomError,
  BattleStateReadError,
  BattleStateService,
  RealtimeRoomHeartbeatCommand
}
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}
import slaydemo.backend.shared.routes.HttpRouteSupport

final class BattleRoutes(
  queueService: BattleQueueService,
  battleStateService: BattleStateService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService
) {
  private val StateStreamSleepMs: Long = 33L

  def join(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, 400, "bad_request", message)
        case Right(fields) =>
          parseJoinCommand(fields) match {
            case Left(message) =>
              jsonError(exchange, 400, "bad_request", message)
            case Right(Left(BattleQueueJoinCommandParseError.InvalidHandle)) =>
              jsonError(exchange, 400, "invalid_handle", "Handle must be a playable non-visitor handle.")
            case Right(Left(BattleQueueJoinCommandParseError.MissingSession)) =>
              jsonError(exchange, 401, "missing_session", "Session token is required.")
            case Right(Right(command)) =>
              joinAuthorizationService.authorize(command) match {
                case Left(BattleQueueJoinAuthorizationError.InvalidSession) =>
                  jsonError(exchange, 401, "invalid_session", "Session token is not valid.")
                case Left(BattleQueueJoinAuthorizationError.HandleMismatch) =>
                  jsonError(exchange, 403, "identity_mismatch", "Session does not belong to the requested handle.")
                case Right(()) =>
                  val snapshot = queueService.join(command)
                  HttpRouteSupport.sendJson(exchange, 200, renderQueueSnapshot(snapshot))
              }
          }
      }
    }

  def status(exchange: HttpExchange): Unit =
    handleGet(exchange) {
      queryParams(exchange).get("ticketId").flatMap(nonEmptyText).map(TicketId.apply) match {
        case None =>
          jsonError(exchange, 400, "missing_ticket_id", "ticketId query parameter is required.")
        case Some(ticketId) =>
          queueService.status(ticketId) match {
            case Right(snapshot) =>
              HttpRouteSupport.sendJson(exchange, 200, renderQueueSnapshot(snapshot))
            case Left(BattleQueueStatusError.TicketNotFound) =>
              jsonError(exchange, 404, "ticket_not_found", "Queue ticket was not found.")
          }
      }
    }

  def leave(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, 400, "bad_request", message)
        case Right(fields) =>
          parseLeaveRequest(fields) match {
            case Left(message) =>
              jsonError(exchange, 400, "bad_request", message)
            case Right(request) =>
              val left = queueService.leave(TicketId(request.ticketId)) == BattleQueueLeaveOutcome.LeftQueue
              HttpRouteSupport.sendJson(exchange, 200, s"""{"left":$left}""")
          }
      }
    }

  def rooms(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          handleRoomSnapshot(exchange)
        case "POST" =>
          handleRoomHeartbeat(exchange)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET, POST, and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  def state(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" if routePath(exchange.getRequestURI.getPath) == "/battle/state/stream" =>
          handleStateStream(exchange)
        case "GET" =>
          handleStateRead(exchange)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET, HEAD, and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  def commands(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, 400, "bad_request", message)
        case Right(fields) =>
          parseCommandRequest(fields) match {
            case Left(BattleCommandRequestParseError.MissingTicket) =>
              jsonError(exchange, 403, "command_not_authorized", "command_not_authorized")
            case Left(BattleCommandRequestParseError.BadRequest(message)) =>
              jsonError(exchange, 400, message, message)
            case Right(request) =>
              battleStateService.acceptCommand(request) match {
                case Right(accepted) =>
                  HttpRouteSupport.sendJson(exchange, 200, BattleStateJson.renderCommandAccepted(accepted))
                case Left(BattleCommandSubmitError.BattleNotFound) =>
                  jsonError(exchange, 404, "battle_not_found", "battle_not_found")
                case Left(BattleCommandSubmitError.PlayerNotFound) =>
                  jsonError(exchange, 400, "player_not_found", "player_not_found")
                case Left(BattleCommandSubmitError.BotCommandsNotSupported) =>
                  jsonError(exchange, 400, "bot_commands_not_supported", "bot_commands_not_supported")
                case Left(BattleCommandSubmitError.CommandNotAuthorized) =>
                  jsonError(exchange, 403, "command_not_authorized", "command_not_authorized")
              }
          }
      }
    }

  private def handleStateRead(exchange: HttpExchange): Unit =
    battleIdFromStatePath(exchange.getRequestURI.getPath)
      .orElse(queryParams(exchange).get("battleId").flatMap(nonEmptyText).map(BattleId.apply)) match {
      case None =>
        jsonError(exchange, 400, "invalid_battle_id", "battleId is required.")
      case Some(battleId) =>
        battleStateService.currentState(battleId) match {
          case Right(state) =>
            HttpRouteSupport.sendJson(exchange, 200, BattleStateJson.renderState(state))
          case Left(BattleStateReadError.BattleNotFound) =>
            jsonError(exchange, 404, "battle_not_found", "battle_not_found")
        }
    }

  private def handleStateStream(exchange: HttpExchange): Unit =
    queryParams(exchange).get("battleId").flatMap(nonEmptyText).map(BattleId.apply) match {
      case None =>
        jsonError(exchange, 400, "invalid_battle_id", "battleId is required.")
      case Some(battleId) =>
        battleStateService.currentState(battleId) match {
          case Left(BattleStateReadError.BattleNotFound) =>
            jsonError(exchange, 404, "battle_not_found", "battle_not_found")
          case Right(state) =>
            val headers = exchange.getResponseHeaders
            headers.set("Content-Type", "text/event-stream; charset=utf-8")
            headers.set("Cache-Control", "no-cache")
            headers.set("Connection", "keep-alive")
            exchange.sendResponseHeaders(200, 0)
            val output = exchange.getResponseBody
            try {
              writeStateStreamFrames(output, battleId, state)
            } catch {
              case _: IOException =>
              case _: InterruptedException =>
                Thread.currentThread().interrupt()
            } finally {
              output.close()
            }
        }
    }

  private def writeStateStreamFrames(
    output: java.io.OutputStream,
    battleId: BattleId,
    initialState: BattleAggregateState
  ): Unit = {
    var nextState = Option(initialState)
    var streaming = true

    while streaming do {
      nextState match {
        case None =>
          streaming = false
        case Some(state) =>
          val frame = s"event: state\ndata: ${BattleStateJson.renderState(state)}\n\n"
          output.write(frame.getBytes(StandardCharsets.UTF_8))
          output.flush()

          if state.phase == BattlePhase.Finished then streaming = false
          else {
            Thread.sleep(StateStreamSleepMs)
            nextState = battleStateService.currentState(battleId).toOption
          }
      }
    }
  }

  private def handleRoomSnapshot(exchange: HttpExchange): Unit = {
    val path = routePath(exchange.getRequestURI.getPath)
    roomIdFromSnapshotPath(path).orElse(queryParams(exchange).get("roomId").flatMap(nonEmptyText).map(RoomId.apply)) match {
      case None if path == "/battle/rooms/snapshot" || path.endsWith("/snapshot") =>
        jsonError(exchange, 400, "invalid_room_id", "roomId is required.")
      case None =>
        jsonError(exchange, 404, "route_not_found", "Battle room route was not found.")
      case Some(roomId) =>
        sendRoomSnapshotResult(exchange, queueService.roomSnapshot(roomId))
    }
  }

  private def handleRoomHeartbeat(exchange: HttpExchange): Unit = {
    val path = routePath(exchange.getRequestURI.getPath)
    val pathRoomId = roomIdFromHeartbeatPath(path)
    val isHeartbeatRoute = path == "/battle/rooms/heartbeat" || pathRoomId.isDefined

    if !isHeartbeatRoute then
      jsonError(exchange, 404, "route_not_found", "Battle room route was not found.")
    else
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, 400, "bad_request", message)
        case Right(fields) =>
          val query = queryParams(exchange)
          val bodyRequest = RealtimeRoomHeartbeatRequest(
            roomId = readString(fields, "roomId"),
            ticketId = readString(fields, "ticketId"),
            handle = readString(fields, "handle")
          )
          val command = RealtimeRoomHeartbeatCommand(
            roomId = pathRoomId
              .orElse(bodyRequest.roomId.flatMap(nonEmptyText).map(RoomId.apply))
              .orElse(query.get("roomId").flatMap(nonEmptyText).map(RoomId.apply)),
            ticketId = bodyRequest.ticketId
              .flatMap(nonEmptyText)
              .map(TicketId.apply)
              .orElse(query.get("ticketId").flatMap(nonEmptyText).map(TicketId.apply)),
            handle = bodyRequest.handle
              .flatMap(nonEmptyText)
              .orElse(query.get("handle").flatMap(nonEmptyText))
              .flatMap(PlayerHandle.forLookup)
          )

          sendRoomSnapshotResult(exchange, queueService.heartbeat(command))
      }
  }

  private def handlePost(exchange: HttpExchange)(action: => Unit): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "POST" =>
          action
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only POST and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def handleGet(exchange: HttpExchange)(action: => Unit): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          action
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def parseJoinCommand(
    fields: Map[String, BattleJsonValue]
  ): Either[String, Either[BattleQueueJoinCommandParseError, BattleQueueJoinCommand]] =
    readOptionalInt(fields, "rating").map { rating =>
      for {
        handle <- PlayerHandle.forLookup(readString(fields, "handle").getOrElse(""))
          .toRight(BattleQueueJoinCommandParseError.InvalidHandle)
        sessionToken <- SessionToken.fromString(readString(fields, "sessionToken").getOrElse(""))
          .toRight(BattleQueueJoinCommandParseError.MissingSession)
      } yield BattleQueueJoinCommand(
        handle = handle,
        sessionToken = sessionToken,
        queueRequestId = readString(fields, "queueRequestId").flatMap(nonEmptyText).map(QueueRequestId.apply),
        rating = rating.map(Rating.apply),
        avatar = readString(fields, "avatar").flatMap(nonEmptyText),
        skin = readString(fields, "skin").flatMap(nonEmptyText)
      )
    }

  private def parseLeaveRequest(fields: Map[String, BattleJsonValue]): Either[String, BattleQueueLeaveRequest] =
    readString(fields, "ticketId").flatMap(nonEmptyText) match {
      case Some(ticketId) => Right(BattleQueueLeaveRequest(ticketId))
      case None           => Left("ticketId is required.")
    }

  private def parseCommandRequest(fields: Map[String, BattleJsonValue]): Either[BattleCommandRequestParseError, BattleCommandRequest] =
    for {
      battleId <- readRequiredCommandString(fields, "battleId", "missing_battle_id").map(BattleId.apply).left.map(BattleCommandRequestParseError.BadRequest.apply)
      playerId <- readRequiredCommandString(fields, "playerId", "missing_player_id").map(PlayerId.apply).left.map(BattleCommandRequestParseError.BadRequest.apply)
      ticketId <- readCommandString(fields, "ticketId").flatMap(nonEmptyText).map(TicketId.apply).toRight(BattleCommandRequestParseError.MissingTicket)
      clientTick <- readRequiredCommandLong(fields, "clientTick", "missing_client_tick").map(BattleTick.apply).left.map(BattleCommandRequestParseError.BadRequest.apply)
      clientCommandSeq = readCommandLong(fields, "clientCommandSeq").map(ClientCommandSeq.apply).getOrElse(ClientCommandSeq(clientTick.value))
      movement <- readCommandVector(fields, "movement").toRight(BattleCommandRequestParseError.BadRequest("missing_movement"))
      aim <- readCommandVector(fields, "aim").toRight(BattleCommandRequestParseError.BadRequest("missing_aim"))
      primaryHeld <- readCommandBoolean(fields, "primaryHeld").toRight(BattleCommandRequestParseError.BadRequest("missing_primary_held"))
      reloadPressed <- readCommandBoolean(fields, "reloadPressed").toRight(BattleCommandRequestParseError.BadRequest("missing_reload_pressed"))
      switchWeaponDirection <- readCommandInt(fields, "switchWeaponDirection").toRight(BattleCommandRequestParseError.BadRequest("missing_switch_weapon_direction"))
    } yield BattleCommandRequest(
      battleId = battleId,
      playerId = playerId,
      ticketId = ticketId,
      clientTick = clientTick,
      clientCommandSeq = clientCommandSeq,
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = readCommandBoolean(fields, "sprint").getOrElse(false),
      reloadPressed = reloadPressed,
      castDash = readCommandBoolean(fields, "castDash").getOrElse(false),
      castBlink = readCommandBoolean(fields, "castBlink").getOrElse(false),
      castFreeze = readCommandBoolean(fields, "castFreeze").getOrElse(false),
      pointerWorld = readCommandVector(fields, "pointerWorld"),
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = readCommandInt(fields, "switchWeaponIndex")
    )

  private def sendRoomSnapshotResult(
    exchange: HttpExchange,
    result: Either[BattleRoomError, RealtimeRoomSnapshot]
  ): Unit =
    result match {
      case Right(snapshot) =>
        HttpRouteSupport.sendJson(exchange, 200, renderRoomSnapshot(snapshot))
      case Left(BattleRoomError.MissingRoomId) =>
        jsonError(exchange, 400, "invalid_room_id", "roomId is required.")
      case Left(BattleRoomError.RoomNotFound) =>
        jsonError(exchange, 404, "room_not_found", "Battle room was not found.")
    }

  private def roomIdFromSnapshotPath(path: String): Option[RoomId] =
    roomIdFromRoomPath(path, "snapshot")

  private def roomIdFromHeartbeatPath(path: String): Option[RoomId] =
    roomIdFromRoomPath(path, "heartbeat")

  private def roomIdFromRoomPath(path: String, terminal: String): Option[RoomId] = {
    val prefix = "/battle/rooms/"
    if !path.startsWith(prefix) then None
    else
      path.stripPrefix(prefix).split("/", -1).toList match {
        case roomId :: action :: Nil if action == terminal && roomId.nonEmpty && roomId != "snapshot" && roomId != "heartbeat" =>
          Some(RoomId(decode(roomId)))
        case _ =>
          None
      }
  }

  private def battleIdFromStatePath(path: String): Option[BattleId] = {
    val normalized = routePath(path)
    val prefix = "/battle/state/"
    if normalized.startsWith(prefix) && normalized.length > prefix.length then
      nonEmptyText(decode(normalized.substring(prefix.length))).map(BattleId.apply)
    else None
  }

  private def routePath(path: String): String = {
    val raw = Option(path).getOrElse("")
    if raw == "/api" then "/"
    else if raw.startsWith("/api/") then raw.stripPrefix("/api")
    else raw
  }

  private def readJsonObject(exchange: HttpExchange): Either[String, Map[String, BattleJsonValue]] =
    BattleJsonObjectParser
      .parse(HttpRouteSupport.readRequestBody(exchange))
      .left
      .map(_ => "Request body must be a JSON object with supported primitive or object fields.")

  private def queryParams(exchange: HttpExchange): Map[String, String] =
    Option(exchange.getRequestURI.getRawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def readString(fields: Map[String, BattleJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => Some(value)
      case Some(BattleJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(BattleJsonValue.NumberValue(value)) => Some(value.toString)
      case _                                        => None
    }

  private def readRequiredString(
    fields: Map[String, BattleJsonValue],
    key: String,
    error: String
  ): Either[String, String] =
    readString(fields, key).flatMap(nonEmptyText).toRight(error)

  private def readCommandString(fields: Map[String, BattleJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => Some(value)
      case _                                        => None
    }

  private def readRequiredCommandString(
    fields: Map[String, BattleJsonValue],
    key: String,
    error: String
  ): Either[String, String] =
    readCommandString(fields, key).flatMap(nonEmptyText).toRight(error)

  private def readLong(fields: Map[String, BattleJsonValue], key: String): Option[Long] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => nonEmptyText(value).flatMap(_.toLongOption)
      case Some(BattleJsonValue.NumberValue(value)) if isValidLong(value) => Some(value.toLong)
      case _ => None
    }

  private def readCommandLong(fields: Map[String, BattleJsonValue], key: String): Option[Long] =
    fields.get(key) match {
      case Some(BattleJsonValue.NumberValue(value)) if isValidLong(value) => Some(value.toLong)
      case _ => None
    }

  private def readRequiredCommandLong(
    fields: Map[String, BattleJsonValue],
    key: String,
    error: String
  ): Either[String, Long] =
    readCommandLong(fields, key).toRight(error)

  private def readRequiredLong(
    fields: Map[String, BattleJsonValue],
    key: String,
    error: String
  ): Either[String, Long] =
    readLong(fields, key).toRight(error)

  private def readInt(fields: Map[String, BattleJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => nonEmptyText(value).flatMap(_.toIntOption)
      case Some(BattleJsonValue.NumberValue(value)) if isValidInt(value) => Some(value.toInt)
      case _ => None
    }

  private def readCommandInt(fields: Map[String, BattleJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(BattleJsonValue.NumberValue(value)) if isValidInt(value) => Some(value.toInt)
      case _ => None
    }

  private def readDouble(fields: Map[String, BattleJsonValue], key: String): Option[Double] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => nonEmptyText(value).flatMap(_.toDoubleOption)
      case Some(BattleJsonValue.NumberValue(value)) if value.isFinite => Some(value)
      case _ => None
    }

  private def readCommandDouble(fields: Map[String, BattleJsonValue], key: String): Option[Double] =
    fields.get(key) match {
      case Some(BattleJsonValue.NumberValue(value)) if value.isFinite => Some(value)
      case _ => None
    }

  private def readBoolean(fields: Map[String, BattleJsonValue], key: String): Option[Boolean] =
    fields.get(key) match {
      case Some(BattleJsonValue.BooleanValue(value)) => Some(value)
      case Some(BattleJsonValue.StringValue(value)) =>
        nonEmptyText(value).flatMap {
          case "true"  => Some(true)
          case "false" => Some(false)
          case _       => None
        }
      case _ => None
    }

  private def readCommandBoolean(fields: Map[String, BattleJsonValue], key: String): Option[Boolean] =
    fields.get(key) match {
      case Some(BattleJsonValue.BooleanValue(value)) => Some(value)
      case _                                         => None
    }

  private def readVector(fields: Map[String, BattleJsonValue], key: String): Option[BattleCommandVector] =
    fields.get(key) match {
      case Some(BattleJsonValue.ObjectValue(vectorFields)) =>
        for {
          x <- readDouble(vectorFields, "x")
          y <- readDouble(vectorFields, "y")
        } yield BattleCommandVector(x, y)
      case _ => None
    }

  private def readCommandVector(fields: Map[String, BattleJsonValue], key: String): Option[BattleCommandVector] =
    fields.get(key) match {
      case Some(BattleJsonValue.ObjectValue(vectorFields)) =>
        for {
          x <- readCommandDouble(vectorFields, "x")
          y <- readCommandDouble(vectorFields, "y")
        } yield BattleCommandVector(x, y)
      case _ => None
    }

  private def readOptionalInt(fields: Map[String, BattleJsonValue], key: String): Either[String, Option[Int]] =
    fields.get(key) match {
      case None | Some(BattleJsonValue.NullValue) =>
        Right(None)
      case Some(BattleJsonValue.StringValue(value)) =>
        nonEmptyText(value) match {
          case None =>
            Right(None)
          case Some(trimmed) =>
            trimmed.toIntOption.map(value => Right(Some(value))).getOrElse(Left(s"$key must be an integer."))
        }
      case Some(BattleJsonValue.NumberValue(value)) if isValidInt(value) =>
        Right(Some(value.toInt))
      case Some(BattleJsonValue.NumberValue(_)) =>
        Left(s"$key must fit in a 32-bit integer.")
      case _ =>
        Left(s"$key must be an integer.")
    }

  private def isValidInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def isValidLong(value: Double): Boolean =
    value.isWhole && value >= Long.MinValue.toDouble && value <= Long.MaxValue.toDouble

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def renderQueueSnapshot(snapshot: BattleQueueSnapshot): String =
    s"""{"ticketId":${jsonString(snapshot.ticketId.value)},"playerId":${jsonString(snapshot.playerId.value)},"roomId":${jsonString(snapshot.roomId.value)},"createdAt":${snapshot.createdAt.value},"startsAt":${snapshot.startsAt.value},"deadline":${snapshot.deadline.value},"serverTime":${snapshot.serverTime.value},"participants":${renderParticipants(snapshot.participants)},"capacity":${snapshot.capacity.value},"durationMs":${snapshot.durationMs.value},"phase":${jsonString(MatchmakingRoomPhase.wireValue(snapshot.phase))},"finishedAt":${renderOptionalMillis(snapshot.finishedAt)},"battleSession":${renderOptionalBattleSession(snapshot.battleSession)}}"""

  private def renderRoomSnapshot(snapshot: RealtimeRoomSnapshot): String =
    s"""{"roomId":${jsonString(snapshot.roomId.value)},"serverTime":${snapshot.serverTime.value},"participants":${renderParticipants(snapshot.participants)},"capacity":${snapshot.capacity.value},"phase":${jsonString(MatchmakingRoomPhase.wireValue(snapshot.phase))},"finishedAt":${renderOptionalMillis(snapshot.finishedAt)},"battleSession":${renderOptionalBattleSession(snapshot.battleSession)}}"""

  private def renderParticipants(participants: Vector[BattleQueueParticipant]): String =
    participants.map(renderParticipant).mkString("[", ",", "]")

  private def renderParticipant(participant: BattleQueueParticipant): String =
    renderObject(
      Vector(
        "playerId" -> jsonString(participant.playerId.value),
        "handle" -> jsonString(participant.handle.value),
        "joinedAt" -> participant.joinedAt.value.toString,
        "lastSeen" -> participant.lastSeen.value.toString
      ) ++ optionalNumberField("rating", participant.rating.map(_.value)) ++
        optionalStringField("avatar", participant.avatar) ++
        optionalStringField("skin", participant.skin)
    )

  private def renderOptionalBattleSession(session: Option[BattleSessionDescriptor]): String =
    session.map(renderBattleSession).getOrElse("null")

  private def renderBattleSession(session: BattleSessionDescriptor): String =
    renderObject(
      Vector(
        "battleId" -> jsonString(session.battleId.value),
        "startedAt" -> session.startedAt.value.toString,
        "serverTime" -> session.serverTime.value.toString,
        "roster" -> session.roster.map(renderRosterEntry).mkString("[", ",", "]"),
        "capacity" -> session.capacity.value.toString,
        "bootstrap" -> session.bootstrap.map(renderBootstrap).getOrElse("null")
      )
    )

  private def renderRosterEntry(entry: BattleSessionRosterEntry): String =
    renderObject(
      Vector(
        "seat" -> entry.seat.value.toString,
        "playerId" -> jsonString(entry.playerId.value),
        "handle" -> jsonString(entry.handle.value),
        "joinedAt" -> entry.joinedAt.value.toString
      ) ++ optionalNumberField("rating", entry.rating.map(_.value)) ++
        optionalStringField("avatar", entry.avatar) ++
        optionalStringField("skin", entry.skin)
    )

  private def renderBootstrap(bootstrap: BattleSessionBootstrap): String =
    renderObject(Vector("seats" -> bootstrap.seats.map(renderBootstrapSeat).mkString("[", ",", "]")))

  private def renderBootstrapSeat(seat: BattleSessionBootstrapSeat): String =
    renderObject(
      Vector(
        "seat" -> seat.seat.value.toString,
        "playerId" -> jsonString(seat.playerId.value),
        "heroId" -> jsonString(seat.heroId.value),
        "handle" -> jsonString(seat.handle.value),
        "displayName" -> jsonString(seat.displayName.value),
        "joinedAt" -> seat.joinedAt.value.toString,
        "isBot" -> seat.isBot.toString,
        "spawnPointIndex" -> seat.spawnPointIndex.value.toString
      ) ++ optionalNumberField("rating", seat.rating.map(_.value)) ++
        optionalStringField("avatar", seat.avatar) ++
        optionalStringField("skin", seat.skin)
    )

  private def optionalNumberField(key: String, value: Option[Int]): Vector[(String, String)] =
    value.map(number => Vector(key -> number.toString)).getOrElse(Vector.empty)

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderOptionalMillis(value: Option[EpochMillis]): String =
    value.map(_.value.toString).getOrElse("null")

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(
      exchange,
      status,
      s"""{"error":${jsonString(message)},"code":${jsonString(code)}}"""
    )
}

object BattleRoutes {
  def apply(
    queueService: BattleQueueService,
    battleStateService: BattleStateService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService
  ): BattleRoutes =
    new BattleRoutes(queueService, battleStateService, joinAuthorizationService)
}

private enum BattleJsonValue {
  case StringValue(value: String)
  case NumberValue(value: Double)
  case BooleanValue(value: Boolean)
  case ObjectValue(fields: Map[String, BattleJsonValue])
  case NullValue
}

private enum BattleQueueJoinCommandParseError {
  case InvalidHandle
  case MissingSession
}

private enum BattleCommandRequestParseError {
  case BadRequest(message: String)
  case MissingTicket
}

private enum BattleJsonParseError {
  case ExpectedObject
  case ExpectedField
  case ExpectedValue
}

private object BattleJsonObjectParser {
  def parse(body: String): Either[BattleJsonParseError, Map[String, BattleJsonValue]] = {
    val trimmed = Option(body).getOrElse("").trim
    if trimmed.isEmpty then Right(Map.empty)
    else Parser(trimmed).parse()
  }

  private final class Parser(source: String) {
    def parse(): Either[BattleJsonParseError, Map[String, BattleJsonValue]] = {
      parseObject(0) match {
        case Some((fields, nextIndex)) if skipWhitespace(nextIndex) == source.length =>
          Right(fields)
        case _ =>
          Left(BattleJsonParseError.ExpectedObject)
      }
    }

    private def parseValue(start: Int): Option[(BattleJsonValue, Int)] =
      if hasChar(start, '"') then
        parseString(start).map { case (value, next) => BattleJsonValue.StringValue(value) -> next }
      else if hasChar(start, '{') then
        parseObject(start).map { case (fields, next) => BattleJsonValue.ObjectValue(fields) -> next }
      else if startsWith(start, "null") then
        Some(BattleJsonValue.NullValue -> (start + 4))
      else if startsWith(start, "true") then
        Some(BattleJsonValue.BooleanValue(true) -> (start + 4))
      else if startsWith(start, "false") then
        Some(BattleJsonValue.BooleanValue(false) -> (start + 5))
      else parseNumber(start)

    private def parseObject(start: Int): Option[(Map[String, BattleJsonValue], Int)] = {
      var index = skipWhitespace(start)
      if !hasChar(index, '{') then return None
      index = skipWhitespace(index + 1)

      var fields = Map.empty[String, BattleJsonValue]
      if hasChar(index, '}') then return Some(fields -> (index + 1))

      while index < source.length do {
        parseString(index) match {
          case None =>
            return None
          case Some((key, afterKey)) =>
            index = skipWhitespace(afterKey)
            if !hasChar(index, ':') then return None
            index = skipWhitespace(index + 1)

            parseValue(index) match {
              case None =>
                return None
              case Some((value, afterValue)) =>
                fields = fields.updated(key, value)
                index = skipWhitespace(afterValue)
                if hasChar(index, '}') then return Some(fields -> (index + 1))
                if !hasChar(index, ',') then return None
                index = skipWhitespace(index + 1)
            }
        }
      }

      None
    }

    private def parseNumber(start: Int): Option[(BattleJsonValue, Int)] = {
      var index = start
      if hasChar(index, '-') then index += 1
      val digitsStart = index
      while index < source.length && source.charAt(index).isDigit do index += 1

      if index == digitsStart then None
      else
        if hasChar(index, '.') then {
          index += 1
          val fractionStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == fractionStart then return None
        }
        if hasChar(index, 'e') || hasChar(index, 'E') then {
          index += 1
          if hasChar(index, '+') || hasChar(index, '-') then index += 1
          val exponentStart = index
          while index < source.length && source.charAt(index).isDigit do index += 1
          if index == exponentStart then return None
        }

        val text = source.substring(start, index)
        text.toDoubleOption
          .filter(value => java.lang.Double.isFinite(value))
          .map(value => BattleJsonValue.NumberValue(value) -> index)
    }

    private def parseString(start: Int): Option[(String, Int)] = {
      if !hasChar(start, '"') then return None

      val builder = StringBuilder()
      var index = start + 1
      var escaped = false

      while index < source.length do {
        val char = source.charAt(index)
        if escaped then {
          decodeEscaped(char, index) match {
            case None =>
              return None
            case Some((decoded, nextIndex)) =>
              builder.append(decoded)
              index = nextIndex
              escaped = false
          }
        } else if char == '\\' then {
          escaped = true
          index += 1
        } else if char == '"' then {
          return Some(builder.result() -> (index + 1))
        } else {
          builder.append(char)
          index += 1
        }
      }

      None
    }

    private def decodeEscaped(char: Char, index: Int): Option[(Char, Int)] =
      char match {
        case '"'  => Some('"' -> (index + 1))
        case '\\' => Some('\\' -> (index + 1))
        case '/'  => Some('/' -> (index + 1))
        case 'b'  => Some('\b' -> (index + 1))
        case 'f'  => Some('\f' -> (index + 1))
        case 'n'  => Some('\n' -> (index + 1))
        case 'r'  => Some('\r' -> (index + 1))
        case 't'  => Some('\t' -> (index + 1))
        case 'u'  => decodeUnicodeEscape(index + 1)
        case _    => None
      }

    private def decodeUnicodeEscape(start: Int): Option[(Char, Int)] = {
      val end = start + 4
      if end > source.length then return None

      val hex = source.substring(start, end)
      if !hex.forall(isHexDigit) then None
      else Some(Integer.parseInt(hex, 16).toChar -> end)
    }

    private def skipWhitespace(start: Int): Int = {
      var index = start
      while index < source.length && source.charAt(index).isWhitespace do index += 1
      index
    }

    private def hasChar(index: Int, expected: Char): Boolean =
      index >= 0 && index < source.length && source.charAt(index) == expected

    private def startsWith(index: Int, expected: String): Boolean =
      source.regionMatches(index, expected, 0, expected.length)

    private def isHexDigit(char: Char): Boolean =
      (char >= '0' && char <= '9') ||
        (char >= 'a' && char <= 'f') ||
        (char >= 'A' && char <= 'F')
  }

  private object Parser {
    def apply(source: String): Parser =
      new Parser(source)
  }
}
