package slaydemo.backend.battle.objects.apiTypes

import io.circe.{Encoder, Json}
import io.circe.syntax.*

import slaydemo.backend.battle.objects.*

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

final case class BattleStateResponse private (state: BattleAggregateState)

object BattleStateResponse {
  def fromState(state: BattleAggregateState): BattleStateResponse =
    BattleStateResponse(state)

  given Encoder[BattleStateResponse] =
    Encoder.instance(response => stateJson(response.state))

  def jsonString(state: BattleAggregateState): String =
    fromState(state).asJson.noSpaces

  private def stateJson(state: BattleAggregateState): Json =
    Json.obj(
      "battleId" -> Json.fromString(state.battleId.value),
      "roomId" -> Json.fromString(state.roomId.value),
      "phase" -> Json.fromString(BattlePhase.wireValue(state.phase)),
      "serverTime" -> Json.fromLong(state.serverTime.value),
      "startedAt" -> Json.fromLong(state.startedAt.value),
      "durationMs" -> Json.fromLong(state.durationMs.value),
      "elapsedMs" -> Json.fromLong(state.elapsedMs.value),
      "endsAt" -> Json.fromLong(state.endsAt.value),
      "worldSize" -> vectorJson(state.worldSize),
      "tick" -> Json.fromLong(state.tick.value),
      "resultReady" -> Json.fromBoolean(BattleArtifactStatus.isResultReady(state.artifactStatus)),
      "replayReady" -> Json.fromBoolean(BattleArtifactStatus.isReplayReady(state.artifactStatus)),
      "players" -> Json.fromValues(state.players.map(playerJson)),
      "projectiles" -> Json.fromValues(state.projectiles.map(projectileJson)),
      "projectileTerminals" -> Json.fromValues(state.projectileTerminals.map(projectileTerminalJson)),
      "slowFields" -> Json.fromValues(state.slowFields.map(slowFieldJson)),
      "pickups" -> Json.fromValues(state.pickups.map(pickupJson)),
      "events" -> Json.fromValues(state.events.map(eventJson)),
      "winnerPlayerId" -> optionalStringJson(state.winnerPlayerId.map(_.value)),
      "winnerHeroId" -> optionalStringJson(state.winnerHeroId.map(_.value))
    )

  private def playerJson(player: BattlePlayerState): Json = {
    val currentWeapon = player.weapons.lift(player.currentWeaponIndex).getOrElse(
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
    )

    Json.obj(
      "playerId" -> Json.fromString(player.playerId.value),
      "heroId" -> Json.fromString(player.heroId.value),
      "handle" -> Json.fromString(player.handle.value),
      "displayName" -> Json.fromString(player.displayName.value),
      "seat" -> Json.fromInt(player.seat.value),
      "isBot" -> Json.fromBoolean(player.isBot),
      "position" -> vectorJson(player.position),
      "aim" -> vectorJson(player.aim),
      "facing" -> Json.fromDoubleOrNull(player.facing.value),
      "movement" -> vectorJson(player.movement),
      "sprint" -> Json.fromBoolean(player.sprint),
      "primaryHeld" -> Json.fromBoolean(player.primaryHeld),
      "reloadPressed" -> Json.fromBoolean(player.reloadPressed),
      "lastClientCommandSeq" -> Json.fromLong(player.lastClientCommandSeq.value),
      "currentWeaponIndex" -> Json.fromInt(player.currentWeaponIndex),
      "weapons" -> Json.fromValues(player.weapons.map(weaponJson)),
      "currentWeaponKind" -> Json.fromString(WeaponKind.wireValue(player.currentWeaponKind)),
      "ammoInMagazine" -> Json.fromInt(currentWeapon.ammoInMagazine.value),
      "magazineSize" -> Json.fromInt(currentWeapon.magazineSize.value),
      "reserveAmmo" -> optionalIntJson(currentWeapon.reserveAmmo.map(_.value)),
      "fireCooldownMs" -> Json.fromLong(currentWeapon.fireCooldownMs.value),
      "reloadRemainingMs" -> Json.fromLong(currentWeapon.reloadRemainingMs.value),
      "heat" -> Json.fromInt(currentWeapon.heat),
      "overheated" -> Json.fromBoolean(currentWeapon.overheated),
      "overheatRemainingMs" -> Json.fromLong(currentWeapon.overheatRemainingMs.value),
      "hp" -> Json.fromInt(player.hp.value),
      "maxHp" -> Json.fromInt(player.maxHp.value),
      "stamina" -> Json.fromDoubleOrNull(player.stamina.value),
      "maxStamina" -> Json.fromDoubleOrNull(player.maxStamina.value),
      "score" -> Json.fromInt(player.score.value),
      "kills" -> Json.fromInt(player.kills),
      "skills" -> Json.fromValues(player.skills.map(skillJson)),
      "alive" -> Json.fromBoolean(player.alive),
      "eliminatedAtMs" -> optionalLongJson(player.eliminatedAtMs.map(_.value)),
      "respawnMs" -> Json.fromLong(player.respawnMs.value)
    )
  }

  private def weaponJson(weapon: BattleWeaponState): Json =
    BattleStateWeaponResponse.fromWeapon(weapon).asJson

  private def skillJson(skill: BattlePlayerSkillState): Json =
    BattleStateSkillResponse.fromSkill(skill).asJson

  private def projectileJson(projectile: BattleProjectileState): Json =
    BattleStateProjectileResponse.fromProjectile(projectile).asJson

  private def projectileTerminalJson(terminal: BattleProjectileTerminalState): Json =
    BattleStateProjectileTerminalResponse.fromTerminal(terminal).asJson

  private def slowFieldJson(field: BattleSlowFieldState): Json =
    BattleStateSlowFieldResponse.fromSlowField(field).asJson

  private def pickupJson(pickup: BattlePickupState): Json =
    BattleStatePickupResponse.fromPickup(pickup).asJson

  private def eventJson(event: BattleEventState): Json =
    BattleStateEventResponse.fromEvent(event).asJson

  private def vectorJson(vector: BattleVector2): Json =
    BattleStateVectorResponse.fromVector(vector).asJson

  private def optionalStringJson(value: Option[String]): Json =
    value.filter(_.trim.nonEmpty).map(Json.fromString).getOrElse(Json.Null)

  private def optionalIntJson(value: Option[Int]): Json =
    value.map(Json.fromInt).getOrElse(Json.Null)

  private def optionalLongJson(value: Option[Long]): Json =
    value.map(Json.fromLong).getOrElse(Json.Null)
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
