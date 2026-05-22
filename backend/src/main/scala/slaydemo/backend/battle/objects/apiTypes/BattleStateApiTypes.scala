package slaydemo.backend.battle.objects.apiTypes

import io.circe.{Encoder, Json, JsonObject}
import io.circe.syntax.*

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleStateReadError

object BattleStateRequestTarget {
  private val AllowedReadPaths: Set[String] =
    Set("/battle/state", "/api/battle/state")
  private val AllowedStreamPaths: Set[String] =
    Set("/battle/state/stream", "/api/battle/state/stream")

  def isReadPath(path: String): Boolean =
    AllowedReadPaths.contains(path) || hasStatePathBattleId(path)

  def isStreamPath(path: String): Boolean =
    AllowedStreamPaths.contains(path)

  def battleIdFromRead(path: String, query: Map[String, String]): Option[BattleId] =
    battleIdFromStatePath(path)
      .orElse(battleIdFromQuery(query))

  def battleIdFromStream(query: Map[String, String]): Option[BattleId] =
    battleIdFromQuery(query)

  def hasStatePathBattleId(path: String): Boolean =
    battleIdFromStatePath(path).isDefined

  private def battleIdFromStatePath(path: String): Option[BattleId] = {
    val normalized = path.stripPrefix("/api")
    val prefix = "/battle/state/"
    if normalized.startsWith(prefix) && normalized.length > prefix.length then
      nonEmptyText(normalized.substring(prefix.length)).map(BattleId.apply)
    else None
  }

  private def battleIdFromQuery(query: Map[String, String]): Option[BattleId] =
    query.get("battleId").flatMap(nonEmptyText).map(BattleId.apply)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

enum BattleStateApiErrorCode {
  case InvalidBattleId
  case BattleNotFound
  case MethodNotAllowed
}

object BattleStateApiErrorCode {
  def fromReadError(error: BattleStateReadError): BattleStateApiErrorCode =
    error match {
      case BattleStateReadError.BattleNotFound =>
        BattleStateApiErrorCode.BattleNotFound
    }

  def wireValue(code: BattleStateApiErrorCode): String =
    code match {
      case BattleStateApiErrorCode.InvalidBattleId =>
        "invalid_battle_id"
      case BattleStateApiErrorCode.BattleNotFound =>
        "battle_not_found"
      case BattleStateApiErrorCode.MethodNotAllowed =>
        "method_not_allowed"
    }

  def message(code: BattleStateApiErrorCode): String =
    code match {
      case BattleStateApiErrorCode.InvalidBattleId =>
        "battleId is required."
      case BattleStateApiErrorCode.BattleNotFound =>
        "battle_not_found"
      case BattleStateApiErrorCode.MethodNotAllowed =>
        "Only GET, HEAD, and OPTIONS are supported."
    }

  def statusCode(code: BattleStateApiErrorCode): Int =
    code match {
      case BattleStateApiErrorCode.BattleNotFound =>
        404
      case BattleStateApiErrorCode.MethodNotAllowed =>
        405
      case BattleStateApiErrorCode.InvalidBattleId =>
        400
    }
}

final case class BattleStateResponse private (state: BattleAggregateState)

object BattleStateResponse {
  def fromState(state: BattleAggregateState): BattleStateResponse =
    BattleStateResponse(state)

  given Encoder[BattleStateResponse] =
    Encoder.instance(response => stateJson(response.state))

  def jsonString(state: BattleAggregateState): String =
    fromState(state).asJson.noSpaces

  private def stateJson(state: BattleAggregateState): Json =
    BattleStateRootResponse.fromState(state).asJson
}

private final case class BattleStateRootResponse(
  battleId: String,
  roomId: String,
  phase: String,
  serverTime: Long,
  startedAt: Long,
  durationMs: Long,
  elapsedMs: Long,
  endsAt: Long,
  worldSize: BattleStateVectorResponse,
  tick: Long,
  resultReady: Boolean,
  replayReady: Boolean,
  players: Vector[BattleStatePlayerResponse],
  projectiles: Vector[BattleStateProjectileResponse],
  projectileTerminals: Vector[BattleStateProjectileTerminalResponse],
  slowFields: Vector[BattleStateSlowFieldResponse],
  pickups: Vector[BattleStatePickupResponse],
  events: Vector[BattleStateEventResponse],
  winnerPlayerId: Option[String],
  winnerHeroId: Option[String]
)

private object BattleStateRootResponse {
  given Encoder[BattleStateRootResponse] =
    Encoder.forProduct20(
      "battleId",
      "roomId",
      "phase",
      "serverTime",
      "startedAt",
      "durationMs",
      "elapsedMs",
      "endsAt",
      "worldSize",
      "tick",
      "resultReady",
      "replayReady",
      "players",
      "projectiles",
      "projectileTerminals",
      "slowFields",
      "pickups",
      "events",
      "winnerPlayerId",
      "winnerHeroId"
    )((response: BattleStateRootResponse) =>
      (
        response.battleId,
        response.roomId,
        response.phase,
        response.serverTime,
        response.startedAt,
        response.durationMs,
        response.elapsedMs,
        response.endsAt,
        response.worldSize,
        response.tick,
        response.resultReady,
        response.replayReady,
        response.players,
        response.projectiles,
        response.projectileTerminals,
        response.slowFields,
        response.pickups,
        response.events,
        response.winnerPlayerId,
        response.winnerHeroId
      )
    )

  def fromState(state: BattleAggregateState): BattleStateRootResponse =
    BattleStateRootResponse(
      battleId = state.battleId.value,
      roomId = state.roomId.value,
      phase = BattlePhase.wireValue(state.phase),
      serverTime = state.serverTime.value,
      startedAt = state.startedAt.value,
      durationMs = state.durationMs.value,
      elapsedMs = state.elapsedMs.value,
      endsAt = state.endsAt.value,
      worldSize = BattleStateVectorResponse.fromVector(state.worldSize),
      tick = state.tick.value,
      resultReady = BattleArtifactStatus.isResultReady(state.artifactStatus),
      replayReady = BattleArtifactStatus.isReplayReady(state.artifactStatus),
      players = state.players.map(BattleStatePlayerResponse.fromPlayer),
      projectiles = state.projectiles.map(BattleStateProjectileResponse.fromProjectile),
      projectileTerminals = state.projectileTerminals.map(BattleStateProjectileTerminalResponse.fromTerminal),
      slowFields = state.slowFields.map(BattleStateSlowFieldResponse.fromSlowField),
      pickups = state.pickups.map(BattleStatePickupResponse.fromPickup),
      events = state.events.map(BattleStateEventResponse.fromEvent),
      winnerPlayerId = state.winnerPlayerId.map(_.value).filter(_.trim.nonEmpty),
      winnerHeroId = state.winnerHeroId.map(_.value).filter(_.trim.nonEmpty)
    )
}

private final case class BattleStateVectorResponse(x: Double, y: Double)

private object BattleStateVectorResponse {
  given Encoder[BattleStateVectorResponse] =
    Encoder.forProduct2("x", "y")((response: BattleStateVectorResponse) => (response.x, response.y))

  def fromVector(vector: BattleVector2): BattleStateVectorResponse =
    BattleStateVectorResponse(x = vector.x, y = vector.y)
}

private final case class BattleStateWeaponResponse(
  weaponKind: String,
  ammoInMagazine: Int,
  magazineSize: Int,
  reserveAmmo: Option[Int],
  fireCooldownMs: Long,
  reloadRemainingMs: Long,
  heat: Int,
  overheated: Boolean,
  overheatRemainingMs: Long
)

private object BattleStateWeaponResponse {
  given Encoder[BattleStateWeaponResponse] =
    Encoder.forProduct9(
      "weaponKind",
      "ammoInMagazine",
      "magazineSize",
      "reserveAmmo",
      "fireCooldownMs",
      "reloadRemainingMs",
      "heat",
      "overheated",
      "overheatRemainingMs"
    )((response: BattleStateWeaponResponse) =>
      (
        response.weaponKind,
        response.ammoInMagazine,
        response.magazineSize,
        response.reserveAmmo,
        response.fireCooldownMs,
        response.reloadRemainingMs,
        response.heat,
        response.overheated,
        response.overheatRemainingMs
      )
    )

  def fromWeapon(weapon: BattleWeaponState): BattleStateWeaponResponse =
    BattleStateWeaponResponse(
      weaponKind = WeaponKind.wireValue(weapon.weaponKind),
      ammoInMagazine = weapon.ammoInMagazine.value,
      magazineSize = weapon.magazineSize.value,
      reserveAmmo = weapon.reserveAmmo.map(_.value),
      fireCooldownMs = weapon.fireCooldownMs.value,
      reloadRemainingMs = weapon.reloadRemainingMs.value,
      heat = weapon.heat,
      overheated = weapon.overheated,
      overheatRemainingMs = weapon.overheatRemainingMs.value
    )
}

private final case class BattleStateSkillResponse(
  kind: String,
  cooldownMs: Long,
  activeMs: Long
)

private object BattleStateSkillResponse {
  given Encoder[BattleStateSkillResponse] =
    Encoder.forProduct3("kind", "cooldownMs", "activeMs")((response: BattleStateSkillResponse) =>
      (response.kind, response.cooldownMs, response.activeMs)
    )

  def fromSkill(skill: BattlePlayerSkillState): BattleStateSkillResponse =
    BattleStateSkillResponse(
      kind = SkillKind.wireValue(skill.skillKind),
      cooldownMs = skill.cooldownMs.value,
      activeMs = skill.activeMs.value
    )
}

private final case class BattleStatePlayerIdentityResponse(
  playerId: String,
  heroId: String,
  handle: String,
  displayName: String,
  seat: Int,
  isBot: Boolean
)

private object BattleStatePlayerIdentityResponse {
  given Encoder[BattleStatePlayerIdentityResponse] =
    Encoder.forProduct6("playerId", "heroId", "handle", "displayName", "seat", "isBot")(
      (response: BattleStatePlayerIdentityResponse) =>
        (response.playerId, response.heroId, response.handle, response.displayName, response.seat, response.isBot)
    )
}

private final case class BattleStatePlayerControlResponse(
  position: BattleStateVectorResponse,
  aim: BattleStateVectorResponse,
  facing: Double,
  movement: BattleStateVectorResponse,
  sprint: Boolean,
  primaryHeld: Boolean,
  reloadPressed: Boolean,
  lastClientCommandSeq: Long
)

private object BattleStatePlayerControlResponse {
  given Encoder[BattleStatePlayerControlResponse] =
    Encoder.forProduct8("position", "aim", "facing", "movement", "sprint", "primaryHeld", "reloadPressed", "lastClientCommandSeq")(
      (response: BattleStatePlayerControlResponse) =>
        (
          response.position,
          response.aim,
          response.facing,
          response.movement,
          response.sprint,
          response.primaryHeld,
          response.reloadPressed,
          response.lastClientCommandSeq
        )
    )
}

private final case class BattleStatePlayerWeaponSummaryResponse(
  currentWeaponIndex: Int,
  weapons: Vector[BattleStateWeaponResponse],
  currentWeaponKind: String,
  ammoInMagazine: Int,
  magazineSize: Int,
  reserveAmmo: Option[Int],
  fireCooldownMs: Long,
  reloadRemainingMs: Long,
  heat: Int,
  overheated: Boolean,
  overheatRemainingMs: Long
)

private object BattleStatePlayerWeaponSummaryResponse {
  given Encoder[BattleStatePlayerWeaponSummaryResponse] =
    Encoder.forProduct11(
      "currentWeaponIndex",
      "weapons",
      "currentWeaponKind",
      "ammoInMagazine",
      "magazineSize",
      "reserveAmmo",
      "fireCooldownMs",
      "reloadRemainingMs",
      "heat",
      "overheated",
      "overheatRemainingMs"
    )((response: BattleStatePlayerWeaponSummaryResponse) =>
      (
        response.currentWeaponIndex,
        response.weapons,
        response.currentWeaponKind,
        response.ammoInMagazine,
        response.magazineSize,
        response.reserveAmmo,
        response.fireCooldownMs,
        response.reloadRemainingMs,
        response.heat,
        response.overheated,
        response.overheatRemainingMs
      )
    )
}

private final case class BattleStatePlayerVitalsResponse(
  hp: Int,
  maxHp: Int,
  stamina: Double,
  maxStamina: Double,
  score: Int,
  kills: Int,
  skills: Vector[BattleStateSkillResponse],
  alive: Boolean,
  eliminatedAtMs: Option[Long],
  respawnMs: Long
)

private object BattleStatePlayerVitalsResponse {
  given Encoder[BattleStatePlayerVitalsResponse] =
    Encoder.forProduct10("hp", "maxHp", "stamina", "maxStamina", "score", "kills", "skills", "alive", "eliminatedAtMs", "respawnMs")(
      (response: BattleStatePlayerVitalsResponse) =>
        (
          response.hp,
          response.maxHp,
          response.stamina,
          response.maxStamina,
          response.score,
          response.kills,
          response.skills,
          response.alive,
          response.eliminatedAtMs,
          response.respawnMs
        )
    )
}

private final case class BattleStatePlayerResponse(
  identity: BattleStatePlayerIdentityResponse,
  control: BattleStatePlayerControlResponse,
  weapon: BattleStatePlayerWeaponSummaryResponse,
  vitals: BattleStatePlayerVitalsResponse
)

private object BattleStatePlayerResponse {
  given Encoder[BattleStatePlayerResponse] =
    Encoder.instance(response =>
      mergeEncodedObjects(response.identity.asJson, response.control.asJson, response.weapon.asJson, response.vitals.asJson)
    )

  def fromPlayer(player: BattlePlayerState): BattleStatePlayerResponse = {
    val currentWeapon = player.weapons.lift(player.currentWeaponIndex).getOrElse(fallbackCurrentWeapon(player))
    BattleStatePlayerResponse(
      identity = BattleStatePlayerIdentityResponse(
        playerId = player.playerId.value,
        heroId = player.heroId.value,
        handle = player.handle.value,
        displayName = player.displayName.value,
        seat = player.seat.value,
        isBot = player.isBot
      ),
      control = BattleStatePlayerControlResponse(
        position = BattleStateVectorResponse.fromVector(player.position),
        aim = BattleStateVectorResponse.fromVector(player.aim),
        facing = player.facing.value,
        movement = BattleStateVectorResponse.fromVector(player.movement),
        sprint = player.sprint,
        primaryHeld = player.primaryHeld,
        reloadPressed = player.reloadPressed,
        lastClientCommandSeq = player.lastClientCommandSeq.value
      ),
      weapon = BattleStatePlayerWeaponSummaryResponse(
        currentWeaponIndex = player.currentWeaponIndex,
        weapons = player.weapons.map(BattleStateWeaponResponse.fromWeapon),
        currentWeaponKind = WeaponKind.wireValue(player.currentWeaponKind),
        ammoInMagazine = currentWeapon.ammoInMagazine.value,
        magazineSize = currentWeapon.magazineSize.value,
        reserveAmmo = currentWeapon.reserveAmmo.map(_.value),
        fireCooldownMs = currentWeapon.fireCooldownMs.value,
        reloadRemainingMs = currentWeapon.reloadRemainingMs.value,
        heat = currentWeapon.heat,
        overheated = currentWeapon.overheated,
        overheatRemainingMs = currentWeapon.overheatRemainingMs.value
      ),
      vitals = BattleStatePlayerVitalsResponse(
        hp = player.hp.value,
        maxHp = player.maxHp.value,
        stamina = player.stamina.value,
        maxStamina = player.maxStamina.value,
        score = player.score.value,
        kills = player.kills,
        skills = player.skills.map(BattleStateSkillResponse.fromSkill),
        alive = player.alive,
        eliminatedAtMs = player.eliminatedAtMs.map(_.value),
        respawnMs = player.respawnMs.value
      )
    )
  }

  private def fallbackCurrentWeapon(player: BattlePlayerState): BattleWeaponState =
    BattleWeaponState(
      weaponKind = player.currentWeaponKind,
      ammoInMagazine = AmmoCount(0),
      magazineSize = AmmoCount(0),
      reserveAmmo = None,
      fireCooldownMs = CooldownMillis(0),
      reloadRemainingMs = CooldownMillis(0),
      heat = 0,
      thermalState = BattleWeaponThermalState.Ready
    )

  private def mergeEncodedObjects(values: Json*): Json =
    values.foldLeft(Json.fromJsonObject(JsonObject.empty))((merged, value) => merged.deepMerge(value))
}

private final case class BattleStateProjectileResponse(
  projectileId: String,
  ownerHeroId: String,
  kind: String,
  position: BattleStateVectorResponse,
  velocity: BattleStateVectorResponse,
  facing: Double,
  radius: Double,
  damage: Int,
  ttlMs: Long,
  maxLifetimeMs: Long,
  splashRadius: Double
)

private object BattleStateProjectileResponse {
  given Encoder[BattleStateProjectileResponse] =
    Encoder.forProduct11(
      "projectileId",
      "ownerHeroId",
      "kind",
      "position",
      "velocity",
      "facing",
      "radius",
      "damage",
      "ttlMs",
      "maxLifetimeMs",
      "splashRadius"
    )((response: BattleStateProjectileResponse) =>
      (
        response.projectileId,
        response.ownerHeroId,
        response.kind,
        response.position,
        response.velocity,
        response.facing,
        response.radius,
        response.damage,
        response.ttlMs,
        response.maxLifetimeMs,
        response.splashRadius
      )
    )

  def fromProjectile(projectile: BattleProjectileState): BattleStateProjectileResponse =
    BattleStateProjectileResponse(
      projectileId = projectile.projectileId.value,
      ownerHeroId = projectile.ownerHeroId.value,
      kind = ProjectileKind.wireValue(projectile.projectileKind),
      position = BattleStateVectorResponse.fromVector(projectile.position),
      velocity = BattleStateVectorResponse.fromVector(projectile.velocity),
      facing = projectile.facing.value,
      radius = projectile.radius.value,
      damage = projectile.damage.value,
      ttlMs = projectile.ttlMs.value,
      maxLifetimeMs = projectile.maxLifetimeMs.value,
      splashRadius = projectile.splashRadius.value
    )
}

private final case class BattleStateProjectileTerminalResponse(
  projectileId: String,
  kind: String,
  ownerPlayerId: String,
  ownerHeroId: String,
  reason: String,
  start: BattleStateVectorResponse,
  end: BattleStateVectorResponse,
  terminalPosition: BattleStateVectorResponse,
  ttlBefore: Long,
  ttlAfter: Long,
  elapsedMs: Long,
  targetPlayerId: Option[String],
  targetHeroId: Option[String],
  hpBefore: Option[Int],
  hpAfter: Option[Int],
  damage: Option[Int]
)

private object BattleStateProjectileTerminalResponse {
  given Encoder[BattleStateProjectileTerminalResponse] =
    Encoder.forProduct16(
      "projectileId",
      "kind",
      "ownerPlayerId",
      "ownerHeroId",
      "reason",
      "start",
      "end",
      "terminalPosition",
      "ttlBefore",
      "ttlAfter",
      "elapsedMs",
      "targetPlayerId",
      "targetHeroId",
      "hpBefore",
      "hpAfter",
      "damage"
    )((response: BattleStateProjectileTerminalResponse) =>
      (
        response.projectileId,
        response.kind,
        response.ownerPlayerId,
        response.ownerHeroId,
        response.reason,
        response.start,
        response.end,
        response.terminalPosition,
        response.ttlBefore,
        response.ttlAfter,
        response.elapsedMs,
        response.targetPlayerId,
        response.targetHeroId,
        response.hpBefore,
        response.hpAfter,
        response.damage
      )
    )

  def fromTerminal(terminal: BattleProjectileTerminalState): BattleStateProjectileTerminalResponse =
    BattleStateProjectileTerminalResponse(
      projectileId = terminal.projectileId.value,
      kind = ProjectileKind.wireValue(terminal.projectileKind),
      ownerPlayerId = terminal.ownerPlayerId.value,
      ownerHeroId = terminal.ownerHeroId.value,
      reason = ProjectileTerminalReason.wireValue(terminal.reason),
      start = BattleStateVectorResponse.fromVector(terminal.start),
      end = BattleStateVectorResponse.fromVector(terminal.end),
      terminalPosition = BattleStateVectorResponse.fromVector(terminal.terminalPosition),
      ttlBefore = terminal.ttlBefore.value,
      ttlAfter = terminal.ttlAfter.value,
      elapsedMs = terminal.elapsedMs.value,
      targetPlayerId = terminal.targetPlayerId.map(_.value),
      targetHeroId = terminal.targetHeroId.map(_.value),
      hpBefore = terminal.hpBefore.map(_.value),
      hpAfter = terminal.hpAfter.map(_.value),
      damage = terminal.damage.map(_.value)
    )
}

private final case class BattleStateSlowFieldResponse(
  fieldId: String,
  ownerPlayerId: String,
  ownerHeroId: String,
  position: BattleStateVectorResponse,
  radius: Double,
  ttlMs: Long,
  durationMs: Long
)

private object BattleStateSlowFieldResponse {
  given Encoder[BattleStateSlowFieldResponse] =
    Encoder.forProduct7("fieldId", "ownerPlayerId", "ownerHeroId", "position", "radius", "ttlMs", "durationMs")(
      (response: BattleStateSlowFieldResponse) =>
        (
          response.fieldId,
          response.ownerPlayerId,
          response.ownerHeroId,
          response.position,
          response.radius,
          response.ttlMs,
          response.durationMs
        )
    )

  def fromSlowField(field: BattleSlowFieldState): BattleStateSlowFieldResponse =
    BattleStateSlowFieldResponse(
      fieldId = field.fieldId.value,
      ownerPlayerId = field.ownerPlayerId.value,
      ownerHeroId = field.ownerHeroId.value,
      position = BattleStateVectorResponse.fromVector(field.position),
      radius = field.radius.value,
      ttlMs = field.ttlMs.value,
      durationMs = field.durationMs.value
    )
}

private final case class BattleStateEventParticipantResponse(
  playerId: String,
  heroId: String,
  displayName: String
)

private object BattleStateEventParticipantResponse {
  given Encoder[BattleStateEventParticipantResponse] =
    Encoder.forProduct3("playerId", "heroId", "displayName")((response: BattleStateEventParticipantResponse) =>
      (response.playerId, response.heroId, response.displayName)
    )

  def fromParticipant(participant: BattleEventParticipant): BattleStateEventParticipantResponse =
    BattleStateEventParticipantResponse(
      playerId = participant.playerId.value,
      heroId = participant.heroId.value,
      displayName = participant.displayName.value
    )
}

private final case class BattleStateEventResponse(
  eventId: String,
  `type`: String,
  kind: String,
  elapsedMs: Long,
  message: String,
  source: BattleStateEventParticipantResponse,
  target: BattleStateEventParticipantResponse
)

private object BattleStateEventResponse {
  given Encoder[BattleStateEventResponse] =
    Encoder.forProduct7("eventId", "type", "kind", "elapsedMs", "message", "source", "target")(
      (response: BattleStateEventResponse) =>
        (
          response.eventId,
          response.`type`,
          response.kind,
          response.elapsedMs,
          response.message,
          response.source,
          response.target
        )
    )

  def fromEvent(event: BattleEventState): BattleStateEventResponse = {
    val eventKind = BattleEventKind.wireValue(event.eventKind)
    BattleStateEventResponse(
      eventId = event.eventId.value,
      `type` = eventKind,
      kind = eventKind,
      elapsedMs = event.elapsedMs.value,
      message = event.message,
      source = BattleStateEventParticipantResponse.fromParticipant(event.source),
      target = BattleStateEventParticipantResponse.fromParticipant(event.target)
    )
  }
}

private final case class BattleStatePickupResponse(
  pickupId: String,
  kind: String,
  position: BattleStateVectorResponse,
  available: Boolean,
  respawnMs: Long,
  weaponKind: Option[String]
)

private object BattleStatePickupResponse {
  given Encoder[BattleStatePickupResponse] =
    Encoder
      .forProduct6("pickupId", "kind", "position", "available", "respawnMs", "weaponKind")(
        (response: BattleStatePickupResponse) =>
          (
            response.pickupId,
            response.kind,
            response.position,
            response.available,
            response.respawnMs,
            response.weaponKind
          )
      )
      .mapJson(_.dropNullValues)

  def fromPickup(pickup: BattlePickupState): BattleStatePickupResponse =
    BattleStatePickupResponse(
      pickupId = pickup.pickupId.value,
      kind = PickupKind.wireValue(pickup.pickupKind),
      position = BattleStateVectorResponse.fromVector(pickup.position),
      available = pickup.available,
      respawnMs = pickup.respawnMs.value,
      weaponKind = pickup.weaponKind.map(WeaponKind.wireValue).filter(_.trim.nonEmpty)
    )
}
