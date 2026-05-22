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
    Json.obj(
      "weaponKind" -> Json.fromString(WeaponKind.wireValue(weapon.weaponKind)),
      "ammoInMagazine" -> Json.fromInt(weapon.ammoInMagazine.value),
      "magazineSize" -> Json.fromInt(weapon.magazineSize.value),
      "reserveAmmo" -> optionalIntJson(weapon.reserveAmmo.map(_.value)),
      "fireCooldownMs" -> Json.fromLong(weapon.fireCooldownMs.value),
      "reloadRemainingMs" -> Json.fromLong(weapon.reloadRemainingMs.value),
      "heat" -> Json.fromInt(weapon.heat),
      "overheated" -> Json.fromBoolean(weapon.overheated),
      "overheatRemainingMs" -> Json.fromLong(weapon.overheatRemainingMs.value)
    )

  private def skillJson(skill: BattlePlayerSkillState): Json =
    Json.obj(
      "kind" -> Json.fromString(SkillKind.wireValue(skill.skillKind)),
      "cooldownMs" -> Json.fromLong(skill.cooldownMs.value),
      "activeMs" -> Json.fromLong(skill.activeMs.value)
    )

  private def projectileJson(projectile: BattleProjectileState): Json =
    Json.obj(
      "projectileId" -> Json.fromString(projectile.projectileId.value),
      "ownerHeroId" -> Json.fromString(projectile.ownerHeroId.value),
      "kind" -> Json.fromString(ProjectileKind.wireValue(projectile.projectileKind)),
      "position" -> vectorJson(projectile.position),
      "velocity" -> vectorJson(projectile.velocity),
      "facing" -> Json.fromDoubleOrNull(projectile.facing.value),
      "radius" -> Json.fromDoubleOrNull(projectile.radius.value),
      "damage" -> Json.fromInt(projectile.damage.value),
      "ttlMs" -> Json.fromLong(projectile.ttlMs.value),
      "maxLifetimeMs" -> Json.fromLong(projectile.maxLifetimeMs.value),
      "splashRadius" -> Json.fromDoubleOrNull(projectile.splashRadius.value)
    )

  private def projectileTerminalJson(terminal: BattleProjectileTerminalState): Json =
    Json.obj(
      "projectileId" -> Json.fromString(terminal.projectileId.value),
      "kind" -> Json.fromString(ProjectileKind.wireValue(terminal.projectileKind)),
      "ownerPlayerId" -> Json.fromString(terminal.ownerPlayerId.value),
      "ownerHeroId" -> Json.fromString(terminal.ownerHeroId.value),
      "reason" -> Json.fromString(ProjectileTerminalReason.wireValue(terminal.reason)),
      "start" -> vectorJson(terminal.start),
      "end" -> vectorJson(terminal.end),
      "terminalPosition" -> vectorJson(terminal.terminalPosition),
      "ttlBefore" -> Json.fromLong(terminal.ttlBefore.value),
      "ttlAfter" -> Json.fromLong(terminal.ttlAfter.value),
      "elapsedMs" -> Json.fromLong(terminal.elapsedMs.value),
      "targetPlayerId" -> optionalStringJson(terminal.targetPlayerId.map(_.value)),
      "targetHeroId" -> optionalStringJson(terminal.targetHeroId.map(_.value)),
      "hpBefore" -> optionalIntJson(terminal.hpBefore.map(_.value)),
      "hpAfter" -> optionalIntJson(terminal.hpAfter.map(_.value)),
      "damage" -> optionalIntJson(terminal.damage.map(_.value))
    )

  private def slowFieldJson(field: BattleSlowFieldState): Json =
    Json.obj(
      "fieldId" -> Json.fromString(field.fieldId.value),
      "ownerPlayerId" -> Json.fromString(field.ownerPlayerId.value),
      "ownerHeroId" -> Json.fromString(field.ownerHeroId.value),
      "position" -> vectorJson(field.position),
      "radius" -> Json.fromDoubleOrNull(field.radius.value),
      "ttlMs" -> Json.fromLong(field.ttlMs.value),
      "durationMs" -> Json.fromLong(field.durationMs.value)
    )

  private def pickupJson(pickup: BattlePickupState): Json =
    Json.obj(
      (
        Vector(
          "pickupId" -> Json.fromString(pickup.pickupId.value),
          "kind" -> Json.fromString(PickupKind.wireValue(pickup.pickupKind)),
          "position" -> vectorJson(pickup.position),
          "available" -> Json.fromBoolean(pickup.available),
          "respawnMs" -> Json.fromLong(pickup.respawnMs.value)
        ) ++ optionalStringField("weaponKind", pickup.weaponKind.map(WeaponKind.wireValue))
      )*
    )

  private def eventJson(event: BattleEventState): Json = {
    val eventKind = BattleEventKind.wireValue(event.eventKind)
    Json.obj(
      "eventId" -> Json.fromString(event.eventId.value),
      "type" -> Json.fromString(eventKind),
      "kind" -> Json.fromString(eventKind),
      "elapsedMs" -> Json.fromLong(event.elapsedMs.value),
      "message" -> Json.fromString(event.message),
      "source" -> eventParticipantJson(event.source),
      "target" -> eventParticipantJson(event.target)
    )
  }

  private def eventParticipantJson(participant: BattleEventParticipant): Json =
    Json.obj(
      "playerId" -> Json.fromString(participant.playerId.value),
      "heroId" -> Json.fromString(participant.heroId.value),
      "displayName" -> Json.fromString(participant.displayName.value)
    )

  private def vectorJson(vector: BattleVector2): Json =
    BattleStateVectorResponse.fromVector(vector).asJson

  private def optionalStringJson(value: Option[String]): Json =
    value.filter(_.trim.nonEmpty).map(Json.fromString).getOrElse(Json.Null)

  private def optionalIntJson(value: Option[Int]): Json =
    value.map(Json.fromInt).getOrElse(Json.Null)

  private def optionalLongJson(value: Option[Long]): Json =
    value.map(Json.fromLong).getOrElse(Json.Null)

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, Json)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> Json.fromString(text))).getOrElse(Vector.empty)
}

private final case class BattleStateVectorResponse(x: Double, y: Double)

private object BattleStateVectorResponse {
  given Encoder[BattleStateVectorResponse] =
    Encoder.forProduct2("x", "y")((response: BattleStateVectorResponse) => (response.x, response.y))

  def fromVector(vector: BattleVector2): BattleStateVectorResponse =
    BattleStateVectorResponse(x = vector.x, y = vector.y)
}
