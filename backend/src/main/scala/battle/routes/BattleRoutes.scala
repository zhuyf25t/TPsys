package slaydemo.backend.battle.routes

import java.io.{IOException, InputStream}
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import scala.annotation.tailrec

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest, BattleCommandSkillOutcome, BattleCommandVector}
import slaydemo.backend.battle.objects.{
  BattleAggregateState,
  BattleEventParticipant,
  BattleEventState,
  BattlePickupState,
  BattlePlayerState,
  BattlePlayerSkillState,
  BattleProjectileState,
  BattleProjectileTerminalState,
  BattleSlowFieldState,
  BattleWeaponState,
  BattleVector2
}
import slaydemo.backend.battle.services.BattleService
import slaydemo.backend.shared.objects.{BattleId, UserId}

final class BattleRoutes(service: BattleService) {
  private val StateStreamSleepMs = 33L

  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      val path = exchange.getRequestURI.getPath.stripSuffix("/")
      val pathBattleId = battleIdFromPath(path)
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" if path == "/battle/state/stream" =>
          handleStateStream(exchange)
        case "GET" if path.startsWith("/battle/state") =>
          handleStateRead(exchange, pathBattleId)
        case "POST" if path == "/battle/commands" =>
          handleCommandUplink(exchange)
        case "HEAD" =>
          exchange.sendResponseHeaders(200, -1)
        case _ =>
          sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
      }
    } finally {
      exchange.close()
    }
  }

  private def handleStateRead(exchange: HttpExchange, pathBattleId: Option[String]): Unit = {
    val query = parseQuery(exchange.getRequestURI.getRawQuery)
    pathBattleId.orElse(query.get("battleId")).map(_.trim).filter(_.nonEmpty) match {
      case Some(battleId) =>
        service.currentState(BattleId(battleId)) match {
          case Some(state) =>
            sendJson(exchange, 200, renderState(
              state,
              service.isResultReady(state.battleId),
              service.isReplayReady(state.battleId)
            ))
          case None =>
            sendJson(exchange, 404, """{"error":"battle_not_found"}""")
        }
      case None =>
        sendJson(exchange, 400, """{"error":"invalid_battle_id"}""")
    }
  }

  private def handleStateStream(exchange: HttpExchange): Unit = {
    val query = parseQuery(exchange.getRequestURI.getRawQuery)
    query.get("battleId").map(_.trim).filter(_.nonEmpty) match {
      case Some(battleId) =>
        service.currentState(BattleId(battleId)) match {
          case Some(_) =>
            sendStateStream(exchange, BattleId(battleId))
          case None =>
            sendJson(exchange, 404, """{"error":"battle_not_found"}""")
        }
      case None =>
        sendJson(exchange, 400, """{"error":"invalid_battle_id"}""")
    }
  }

  private def sendStateStream(exchange: HttpExchange, battleId: BattleId): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Content-Type", "text/event-stream; charset=utf-8")
    headers.set("Cache-Control", "no-cache")
    headers.set("Connection", "keep-alive")
    exchange.sendResponseHeaders(200, 0)

    val output = exchange.getResponseBody
    try {
      @tailrec
      def writeNextFrame(): Unit =
        service.currentState(battleId) match {
          case Some(state) =>
            val json = renderState(
              state,
              service.isResultReady(state.battleId),
              service.isReplayReady(state.battleId)
            )
            val frame = s"event: state\ndata: ${compactForSse(json)}\n\n"
            output.write(frame.getBytes(StandardCharsets.UTF_8))
            output.flush()
            if (state.phase != "finished") {
              Thread.sleep(StateStreamSleepMs)
              writeNextFrame()
            }
          case None =>
            ()
        }

      writeNextFrame()
    } catch {
      case _: IOException =>
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
    } finally {
      output.close()
    }
  }

  private def handleCommandUplink(exchange: HttpExchange): Unit =
    parseCommandRequest(readBody(exchange.getRequestBody)) match {
      case Right(request) =>
        service.acceptCommand(request) match {
          case Right(accepted) =>
            sendJson(
              exchange,
              200,
              renderCommandAccepted(accepted)
            )
          case Left("battle_not_found") =>
            sendJson(exchange, 404, """{"error":"battle_not_found"}""")
          case Left("player_not_found") =>
            sendJson(exchange, 400, """{"error":"player_not_found"}""")
          case Left("bot_commands_not_supported") =>
            sendJson(exchange, 400, """{"error":"bot_commands_not_supported"}""")
          case Left("command_not_authorized") =>
            sendJson(exchange, 403, """{"error":"command_not_authorized"}""")
          case Left(other) =>
            sendJson(exchange, 400, s"""{"error":"${escape(other)}"}""")
        }
      case Left(error) =>
        sendJson(exchange, 400, s"""{"error":"${escape(error)}"}""")
    }

  private def renderCommandAccepted(accepted: BattleCommandAccepted): String = {
    val reasonFields = accepted.commandReason.map(reason => "\"commandReason\":\"" + escape(reason) + "\"").toVector
    val fields = Vector(
      "\"battleId\":\"" + escape(accepted.battleId.value) + "\"",
      "\"acceptedTick\":" + accepted.acceptedTick,
      "\"acceptedCommandSeq\":" + accepted.acceptedCommandSeq,
      "\"serverTime\":" + accepted.serverTime,
      "\"commandStatus\":\"" + escape(accepted.commandStatus) + "\""
    ) ++ reasonFields ++ Vector(
      "\"outcomes\":[" + accepted.outcomes.map(renderCommandSkillOutcome).mkString(",") + "]"
    )

    fields.mkString("{", ",", "}")
  }

  private def renderCommandSkillOutcome(outcome: BattleCommandSkillOutcome): String = {
    val reasonFields = outcome.reason.map(reason => "\"reason\":\"" + escape(reason) + "\"").toVector
    val fields = Vector(
      "\"action\":\"" + escape(outcome.action) + "\"",
      "\"status\":\"" + escape(outcome.status) + "\""
    ) ++ reasonFields

    fields.mkString("{", ",", "}")
  }

  private def battleIdFromPath(path: String): Option[String] = {
    val prefix = "/battle/state/"
    if (path.startsWith(prefix) && path.length > prefix.length) {
      Some(urlDecode(path.substring(prefix.length))).map(_.trim).filter(_.nonEmpty)
    } else {
      None
    }
  }

  private def renderState(state: BattleAggregateState, resultReady: Boolean, replayReady: Boolean): String = {
    val players = state.players.map(renderPlayer).mkString(",")
    val projectiles = state.projectiles.map(renderProjectile).mkString(",")
    val projectileTerminals = state.projectileTerminals.map(renderProjectileTerminal).mkString(",")
    val slowFields = state.slowFields.map(renderSlowField).mkString(",")
    val pickups = state.pickups.map(renderPickup).mkString(",")
    val events = state.events.map(renderEvent).mkString(",")
    s"""{
       |  "battleId": "${escape(state.battleId.value)}",
       |  "roomId": "${escape(state.roomId)}",
       |  "phase": "${escape(state.phase)}",
       |  "serverTime": ${state.serverTime},
       |  "startedAt": ${state.startedAt},
       |  "durationMs": ${state.durationMs},
       |  "elapsedMs": ${state.elapsedMs},
       |  "endsAt": ${state.endsAt},
       |  "worldSize": ${renderVector(state.worldSize)},
       |  "tick": ${state.tick},
       |  "resultReady": $resultReady,
       |  "replayReady": $replayReady,
       |  "players": [$players],
       |  "projectiles": [$projectiles],
       |  "projectileTerminals": [$projectileTerminals],
       |  "slowFields": [$slowFields],
       |  "pickups": [$pickups],
       |  "events": [$events],
       |  "winnerPlayerId": ${renderOptionalString(state.winnerPlayerId.map(_.value))},
       |  "winnerHeroId": ${renderOptionalString(state.winnerHeroId)}
       |}""".stripMargin
  }

  private def renderPlayer(player: BattlePlayerState): String = {
    val fields = Vector(
      "\"playerId\":\"" + escape(player.playerId.value) + "\"",
      "\"heroId\":\"" + escape(player.heroId) + "\"",
      "\"handle\":\"" + escape(player.handle) + "\"",
      "\"displayName\":\"" + escape(player.displayName) + "\"",
      "\"seat\":" + player.seat,
      "\"isBot\":" + player.isBot,
      "\"position\":" + renderVector(player.position),
      "\"aim\":" + renderVector(player.aim),
      "\"facing\":" + player.facing,
      "\"primaryHeld\":" + player.primaryHeld,
      "\"reloadPressed\":" + player.reloadPressed,
      "\"lastClientCommandSeq\":" + player.lastClientCommandSeq,
      "\"currentWeaponIndex\":" + player.currentWeaponIndex,
      "\"weapons\":[" + player.weapons.map(renderWeapon).mkString(",") + "]",
      "\"currentWeaponKind\":\"" + escape(player.currentWeaponKind) + "\"",
      "\"ammoInMagazine\":" + player.ammoInMagazine,
      "\"magazineSize\":" + player.magazineSize,
      "\"reserveAmmo\":" + player.reserveAmmo,
      "\"fireCooldownMs\":" + player.fireCooldownMs,
      "\"reloadRemainingMs\":" + player.reloadRemainingMs,
      "\"heat\":" + player.heat,
      "\"overheated\":" + player.overheated,
      "\"overheatRemainingMs\":" + player.overheatRemainingMs,
      "\"hp\":" + player.hp,
      "\"maxHp\":" + player.maxHp,
      "\"stamina\":" + player.stamina,
      "\"maxStamina\":" + player.maxStamina,
      "\"score\":" + player.score,
      "\"kills\":" + player.kills,
      "\"skills\":[" + player.skills.map(renderSkill).mkString(",") + "]",
      "\"alive\":" + player.alive,
      "\"eliminatedAtMs\":" + renderOptionalLong(player.eliminatedAtMs),
      "\"respawnMs\":" + math.max(0L, player.respawnMs)
    )

    fields.mkString("{", ",", "}")
  }

  private def renderWeapon(weapon: BattleWeaponState): String = {
    val fields = Vector(
      "\"weaponKind\":\"" + escape(weapon.weaponKind) + "\"",
      "\"ammoInMagazine\":" + weapon.ammoInMagazine,
      "\"magazineSize\":" + weapon.magazineSize,
      "\"reserveAmmo\":" + weapon.reserveAmmo,
      "\"fireCooldownMs\":" + weapon.fireCooldownMs,
      "\"reloadRemainingMs\":" + weapon.reloadRemainingMs,
      "\"heat\":" + weapon.heat,
      "\"overheated\":" + weapon.overheated,
      "\"overheatRemainingMs\":" + weapon.overheatRemainingMs
    )

    fields.mkString("{", ",", "}")
  }

  private def renderSkill(skill: BattlePlayerSkillState): String = {
    val fields = Vector(
      "\"kind\":\"" + escape(skill.kind) + "\"",
      "\"cooldownMs\":" + math.max(0L, skill.cooldownMs),
      "\"activeMs\":" + math.max(0L, skill.activeMs)
    )

    fields.mkString("{", ",", "}")
  }

  private def renderProjectile(projectile: BattleProjectileState): String = {
    val fields = Vector(
      "\"projectileId\":\"" + escape(projectile.projectileId) + "\"",
      "\"ownerHeroId\":\"" + escape(projectile.ownerHeroId) + "\"",
      "\"kind\":\"" + escape(projectile.kind) + "\"",
      "\"position\":" + renderVector(projectile.position),
      "\"velocity\":" + renderVector(projectile.velocity),
      "\"facing\":" + projectile.facing,
      "\"radius\":" + projectile.radius,
      "\"damage\":" + projectile.damage,
      "\"ttlMs\":" + projectile.ttlMs,
      "\"maxLifetimeMs\":" + projectile.maxLifetimeMs,
      "\"splashRadius\":" + projectile.splashRadius
    )

    fields.mkString("{", ",", "}")
  }

  private def renderProjectileTerminal(terminal: BattleProjectileTerminalState): String = {
    val fields = Vector(
      "\"projectileId\":\"" + escape(terminal.projectileId) + "\"",
      "\"kind\":\"" + escape(terminal.kind) + "\"",
      "\"ownerPlayerId\":\"" + escape(terminal.ownerPlayerId.value) + "\"",
      "\"ownerHeroId\":\"" + escape(terminal.ownerHeroId) + "\"",
      "\"reason\":\"" + escape(terminal.reason) + "\"",
      "\"start\":" + renderVector(terminal.start),
      "\"end\":" + renderVector(terminal.end),
      "\"terminalPosition\":" + renderVector(terminal.terminalPosition),
      "\"ttlBefore\":" + math.max(0L, terminal.ttlBefore),
      "\"ttlAfter\":" + math.max(0L, terminal.ttlAfter),
      "\"elapsedMs\":" + math.max(0L, terminal.elapsedMs),
      "\"targetPlayerId\":" + renderOptionalString(terminal.targetPlayerId.map(_.value)),
      "\"targetHeroId\":" + renderOptionalString(terminal.targetHeroId),
      "\"hpBefore\":" + renderOptionalInt(terminal.hpBefore),
      "\"hpAfter\":" + renderOptionalInt(terminal.hpAfter),
      "\"damage\":" + renderOptionalInt(terminal.damage)
    )

    fields.mkString("{", ",", "}")
  }

  private def renderSlowField(field: BattleSlowFieldState): String = {
    val fields = Vector(
      "\"fieldId\":\"" + escape(field.fieldId) + "\"",
      "\"ownerPlayerId\":\"" + escape(field.ownerPlayerId.value) + "\"",
      "\"ownerHeroId\":\"" + escape(field.ownerHeroId) + "\"",
      "\"position\":" + renderVector(field.position),
      "\"radius\":" + field.radius,
      "\"ttlMs\":" + math.max(0L, field.ttlMs),
      "\"durationMs\":" + math.max(0L, field.durationMs)
    )

    fields.mkString("{", ",", "}")
  }

  private def renderPickup(pickup: BattlePickupState): String = {
    val weaponKindFields =
      pickup.weaponKind
        .filter(_ => pickup.kind == "Weapon")
        .map(kind => Vector("\"weaponKind\":\"" + escape(kind) + "\""))
        .getOrElse(Vector.empty)
    val fields = Vector(
      "\"pickupId\":\"" + escape(pickup.pickupId) + "\"",
      "\"kind\":\"" + escape(pickup.kind) + "\"",
      "\"position\":" + renderVector(pickup.position),
      "\"available\":" + pickup.available,
      "\"respawnMs\":" + pickup.respawnMs
    ) ++ weaponKindFields

    fields.mkString("{", ",", "}")
  }

  private def renderEvent(event: BattleEventState): String = {
    val fields = Vector(
      "\"eventId\":\"" + escape(event.eventId) + "\"",
      "\"type\":\"" + escape(event.eventType) + "\"",
      "\"kind\":\"" + escape(event.kind) + "\"",
      "\"elapsedMs\":" + event.elapsedMs,
      "\"message\":\"" + escape(event.message) + "\"",
      "\"source\":" + renderEventParticipant(event.source),
      "\"target\":" + renderEventParticipant(event.target)
    )

    fields.mkString("{", ",", "}")
  }

  private def renderEventParticipant(participant: BattleEventParticipant): String = {
    val fields = Vector(
      "\"playerId\":\"" + escape(participant.playerId.value) + "\"",
      "\"heroId\":\"" + escape(participant.heroId) + "\"",
      "\"displayName\":\"" + escape(participant.displayName) + "\""
    )

    fields.mkString("{", ",", "}")
  }

  private def renderVector(vector: BattleVector2): String =
    s"""{"x":${vector.x},"y":${vector.y}}"""

  private def renderOptionalString(value: Option[String]): String =
    value.filter(_.trim.nonEmpty).map(entry => "\"" + escape(entry) + "\"").getOrElse("null")

  private def renderOptionalLong(value: Option[Long]): String =
    value.map(_.toString).getOrElse("null")

  private def renderOptionalInt(value: Option[Int]): String =
    value.map(_.toString).getOrElse("null")

  private def parseCommandRequest(body: String): Either[String, BattleCommandRequest] =
    for {
      battleId <- readJsonString(body, "battleId").toRight("missing_battle_id")
      playerId <- readJsonString(body, "playerId").toRight("missing_player_id")
      ticketId = readJsonString(body, "ticketId")
      clientTick <- readJsonLong(body, "clientTick").toRight("missing_client_tick")
      clientCommandSeq = readJsonLong(body, "clientCommandSeq").getOrElse(clientTick)
      movement <- readVector(body, "movement").toRight("missing_movement")
      aim <- readVector(body, "aim").toRight("missing_aim")
      primaryHeld <- readJsonBoolean(body, "primaryHeld").toRight("missing_primary_held")
      sprint = readJsonBoolean(body, "sprint").getOrElse(false)
      reloadPressed <- readJsonBoolean(body, "reloadPressed").toRight("missing_reload_pressed")
      castDash = readJsonBoolean(body, "castDash").getOrElse(false)
      castBlink = readJsonBoolean(body, "castBlink").getOrElse(false)
      castFreeze = readJsonBoolean(body, "castFreeze").getOrElse(false)
      pointerWorld = readVector(body, "pointerWorld")
      switchWeaponDirection <- readJsonInt(body, "switchWeaponDirection").toRight("missing_switch_weapon_direction")
      switchWeaponIndex = readJsonInt(body, "switchWeaponIndex")
    } yield BattleCommandRequest(
      battleId = BattleId(battleId),
      playerId = UserId(playerId),
      ticketId = ticketId,
      clientTick = clientTick,
      clientCommandSeq = clientCommandSeq,
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = sprint,
      reloadPressed = reloadPressed,
      castDash = castDash,
      castBlink = castBlink,
      castFreeze = castFreeze,
      pointerWorld = pointerWorld,
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = switchWeaponIndex
    )

  private def readVector(body: String, field: String): Option[BattleCommandVector] =
    readJsonObject(body, field).flatMap { objectBody =>
      for {
        x <- readJsonDouble(objectBody, "x")
        y <- readJsonDouble(objectBody, "y")
      } yield BattleCommandVector(x, y)
    }

  private def readBody(input: InputStream): String =
    new String(input.readAllBytes(), StandardCharsets.UTF_8).trim

  private def readJsonString(body: String, field: String): Option[String] =
    extractGroup(body, s""""${Pattern.quote(field)}"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""").map(unescape)

  private def readJsonLong(body: String, field: String): Option[Long] =
    extractGroup(body, s""""${Pattern.quote(field)}"\\s*:\\s*(-?\\d+)""").flatMap(_.toLongOption)

  private def readJsonInt(body: String, field: String): Option[Int] =
    extractGroup(body, s""""${Pattern.quote(field)}"\\s*:\\s*(-?\\d+)""").flatMap(_.toIntOption)

  private def readJsonDouble(body: String, field: String): Option[Double] =
    extractGroup(body, s""""${Pattern.quote(field)}"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)""").flatMap(_.toDoubleOption)

  private def readJsonBoolean(body: String, field: String): Option[Boolean] =
    extractGroup(body, s""""${Pattern.quote(field)}"\\s*:\\s*(true|false)""").map(_ == "true")

  private def readJsonObject(body: String, field: String): Option[String] =
    extractGroup(body, s""""${Pattern.quote(field)}"\\s*:\\s*\\{([^{}]*)\\}""")

  private def extractGroup(body: String, regex: String): Option[String] = {
    val matcher = Pattern.compile(regex).matcher(body)
    if (matcher.find()) Some(matcher.group(1)) else None
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

  private def compactForSse(json: String): String =
    json.linesIterator.map(_.trim).mkString

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
