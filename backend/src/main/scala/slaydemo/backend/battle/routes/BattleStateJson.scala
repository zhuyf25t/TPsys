package slaydemo.backend.battle.routes

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandSkillOutcome}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.shared.routes.HttpRouteSupport

object BattleStateJson {
  def renderState(state: BattleAggregateState): String =
    renderObject(
      Vector(
        "battleId" -> jsonString(state.battleId.value),
        "roomId" -> jsonString(state.roomId.value),
        "phase" -> jsonString(BattlePhase.wireValue(state.phase)),
        "serverTime" -> state.serverTime.value.toString,
        "startedAt" -> state.startedAt.value.toString,
        "durationMs" -> state.durationMs.value.toString,
        "elapsedMs" -> state.elapsedMs.value.toString,
        "endsAt" -> state.endsAt.value.toString,
        "worldSize" -> renderVector(state.worldSize),
        "tick" -> state.tick.value.toString,
        "resultReady" -> BattleArtifactStatus.isResultReady(state.artifactStatus).toString,
        "replayReady" -> BattleArtifactStatus.isReplayReady(state.artifactStatus).toString,
        "players" -> state.players.map(renderPlayer).mkString("[", ",", "]"),
        "projectiles" -> state.projectiles.map(renderProjectile).mkString("[", ",", "]"),
        "projectileTerminals" -> state.projectileTerminals.map(renderProjectileTerminal).mkString("[", ",", "]"),
        "slowFields" -> state.slowFields.map(renderSlowField).mkString("[", ",", "]"),
        "pickups" -> state.pickups.map(renderPickup).mkString("[", ",", "]"),
        "events" -> state.events.map(renderEvent).mkString("[", ",", "]"),
        "winnerPlayerId" -> renderOptionalString(state.winnerPlayerId.map(_.value)),
        "winnerHeroId" -> renderOptionalString(state.winnerHeroId.map(_.value))
      )
    )

  def renderCommandAccepted(accepted: BattleCommandAccepted): String =
    renderObject(
      Vector(
        "battleId" -> jsonString(accepted.battleId.value),
        "acceptedTick" -> accepted.acceptedTick.value.toString,
        "acceptedCommandSeq" -> accepted.acceptedCommandSeq.value.toString,
        "serverTime" -> accepted.serverTime.value.toString,
        "commandStatus" -> jsonString(BattleCommandStatus.wireValue(accepted.commandStatus)),
        "outcomes" -> accepted.outcomes.map(renderCommandSkillOutcome).mkString("[", ",", "]")
      ) ++ optionalStringField("commandReason", accepted.commandReason.map(BattleCommandReason.wireValue))
    )

  private def renderPlayer(player: BattlePlayerState): String = {
    val currentWeapon = player.weapons.lift(player.currentWeaponIndex).getOrElse(
      BattleWeaponState(
        weaponKind = player.currentWeaponKind,
        ammoInMagazine = AmmoCount(0),
        magazineSize = AmmoCount(0),
        reserveAmmo = None,
        fireCooldownMs = CooldownMillis(0),
        reloadRemainingMs = CooldownMillis(0),
        heat = 0,
        overheated = false,
        overheatRemainingMs = CooldownMillis(0)
      )
    )

    renderObject(
      Vector(
        "playerId" -> jsonString(player.playerId.value),
        "heroId" -> jsonString(player.heroId.value),
        "handle" -> jsonString(player.handle.value),
        "displayName" -> jsonString(player.displayName.value),
        "seat" -> player.seat.value.toString,
        "isBot" -> player.isBot.toString,
        "position" -> renderVector(player.position),
        "aim" -> renderVector(player.aim),
        "facing" -> player.facing.value.toString,
        "movement" -> renderVector(player.movement),
        "sprint" -> player.sprint.toString,
        "primaryHeld" -> player.primaryHeld.toString,
        "reloadPressed" -> player.reloadPressed.toString,
        "lastClientCommandSeq" -> player.lastClientCommandSeq.value.toString,
        "currentWeaponIndex" -> player.currentWeaponIndex.toString,
        "weapons" -> player.weapons.map(renderWeapon).mkString("[", ",", "]"),
        "currentWeaponKind" -> jsonString(WeaponKind.wireValue(player.currentWeaponKind)),
        "ammoInMagazine" -> currentWeapon.ammoInMagazine.value.toString,
        "magazineSize" -> currentWeapon.magazineSize.value.toString,
        "reserveAmmo" -> renderOptionalAmmo(currentWeapon.reserveAmmo),
        "fireCooldownMs" -> currentWeapon.fireCooldownMs.value.toString,
        "reloadRemainingMs" -> currentWeapon.reloadRemainingMs.value.toString,
        "heat" -> currentWeapon.heat.toString,
        "overheated" -> currentWeapon.overheated.toString,
        "overheatRemainingMs" -> currentWeapon.overheatRemainingMs.value.toString,
        "hp" -> player.hp.value.toString,
        "maxHp" -> player.maxHp.value.toString,
        "stamina" -> player.stamina.value.toString,
        "maxStamina" -> player.maxStamina.value.toString,
        "score" -> player.score.value.toString,
        "kills" -> player.kills.toString,
        "skills" -> player.skills.map(renderSkill).mkString("[", ",", "]"),
        "alive" -> player.alive.toString,
        "eliminatedAtMs" -> renderOptionalElapsed(player.eliminatedAtMs),
        "respawnMs" -> player.respawnMs.value.toString
      )
    )
  }

  private def renderWeapon(weapon: BattleWeaponState): String =
    renderObject(
      Vector(
        "weaponKind" -> jsonString(WeaponKind.wireValue(weapon.weaponKind)),
        "ammoInMagazine" -> weapon.ammoInMagazine.value.toString,
        "magazineSize" -> weapon.magazineSize.value.toString,
        "reserveAmmo" -> renderOptionalAmmo(weapon.reserveAmmo),
        "fireCooldownMs" -> weapon.fireCooldownMs.value.toString,
        "reloadRemainingMs" -> weapon.reloadRemainingMs.value.toString,
        "heat" -> weapon.heat.toString,
        "overheated" -> weapon.overheated.toString,
        "overheatRemainingMs" -> weapon.overheatRemainingMs.value.toString
      )
    )

  private def renderSkill(skill: BattlePlayerSkillState): String =
    renderObject(
      Vector(
        "kind" -> jsonString(SkillKind.wireValue(skill.skillKind)),
        "cooldownMs" -> skill.cooldownMs.value.toString,
        "activeMs" -> skill.activeMs.value.toString
      )
    )

  private def renderProjectile(projectile: BattleProjectileState): String =
    renderObject(
      Vector(
        "projectileId" -> jsonString(projectile.projectileId.value),
        "ownerHeroId" -> jsonString(projectile.ownerHeroId.value),
        "kind" -> jsonString(ProjectileKind.wireValue(projectile.projectileKind)),
        "position" -> renderVector(projectile.position),
        "velocity" -> renderVector(projectile.velocity),
        "facing" -> projectile.facing.value.toString,
        "radius" -> projectile.radius.value.toString,
        "damage" -> projectile.damage.value.toString,
        "ttlMs" -> projectile.ttlMs.value.toString,
        "maxLifetimeMs" -> projectile.maxLifetimeMs.value.toString,
        "splashRadius" -> projectile.splashRadius.value.toString
      )
    )

  private def renderProjectileTerminal(terminal: BattleProjectileTerminalState): String =
    renderObject(
      Vector(
        "projectileId" -> jsonString(terminal.projectileId.value),
        "kind" -> jsonString(ProjectileKind.wireValue(terminal.projectileKind)),
        "ownerPlayerId" -> jsonString(terminal.ownerPlayerId.value),
        "ownerHeroId" -> jsonString(terminal.ownerHeroId.value),
        "reason" -> jsonString(ProjectileTerminalReason.wireValue(terminal.reason)),
        "start" -> renderVector(terminal.start),
        "end" -> renderVector(terminal.end),
        "terminalPosition" -> renderVector(terminal.terminalPosition),
        "ttlBefore" -> terminal.ttlBefore.value.toString,
        "ttlAfter" -> terminal.ttlAfter.value.toString,
        "elapsedMs" -> terminal.elapsedMs.value.toString,
        "targetPlayerId" -> renderOptionalString(terminal.targetPlayerId.map(_.value)),
        "targetHeroId" -> renderOptionalString(terminal.targetHeroId.map(_.value)),
        "hpBefore" -> renderOptionalHitPoints(terminal.hpBefore),
        "hpAfter" -> renderOptionalHitPoints(terminal.hpAfter),
        "damage" -> renderOptionalDamage(terminal.damage)
      )
    )

  private def renderSlowField(field: BattleSlowFieldState): String =
    renderObject(
      Vector(
        "fieldId" -> jsonString(field.fieldId.value),
        "ownerPlayerId" -> jsonString(field.ownerPlayerId.value),
        "ownerHeroId" -> jsonString(field.ownerHeroId.value),
        "position" -> renderVector(field.position),
        "radius" -> field.radius.value.toString,
        "ttlMs" -> field.ttlMs.value.toString,
        "durationMs" -> field.durationMs.value.toString
      )
    )

  private def renderPickup(pickup: BattlePickupState): String =
    renderObject(
      Vector(
        "pickupId" -> jsonString(pickup.pickupId.value),
        "kind" -> jsonString(PickupKind.wireValue(pickup.pickupKind)),
        "position" -> renderVector(pickup.position),
        "available" -> pickup.available.toString,
        "respawnMs" -> pickup.respawnMs.value.toString
      ) ++ optionalStringField("weaponKind", pickup.weaponKind.map(WeaponKind.wireValue))
    )

  private def renderEvent(event: BattleEventState): String = {
    val eventKind = BattleEventKind.wireValue(event.eventKind)
    renderObject(
      Vector(
        "eventId" -> jsonString(event.eventId.value),
        "type" -> jsonString(eventKind),
        "kind" -> jsonString(eventKind),
        "elapsedMs" -> event.elapsedMs.value.toString,
        "message" -> jsonString(event.message),
        "source" -> renderEventParticipant(event.source),
        "target" -> renderEventParticipant(event.target)
      )
    )
  }

  private def renderEventParticipant(participant: BattleEventParticipant): String =
    renderObject(
      Vector(
        "playerId" -> jsonString(participant.playerId.value),
        "heroId" -> jsonString(participant.heroId.value),
        "displayName" -> jsonString(participant.displayName.value)
      )
    )

  private def renderCommandSkillOutcome(outcome: BattleCommandSkillOutcome): String =
    renderObject(
      Vector(
        "action" -> jsonString(SkillKind.wireValue(outcome.action)),
        "status" -> jsonString(SkillOutcomeStatus.wireValue(outcome.outcomeStatus))
      ) ++ optionalStringField("reason", outcome.reason.map(SkillOutcomeReason.wireValue))
    )

  private def renderVector(vector: BattleVector2): String =
    renderObject(Vector("x" -> vector.x.toString, "y" -> vector.y.toString))

  private def renderOptionalAmmo(value: Option[AmmoCount]): String =
    value.map(_.value.toString).getOrElse("null")

  private def renderOptionalElapsed(value: Option[ElapsedMillis]): String =
    value.map(_.value.toString).getOrElse("null")

  private def renderOptionalHitPoints(value: Option[HitPoints]): String =
    value.map(_.value.toString).getOrElse("null")

  private def renderOptionalDamage(value: Option[Damage]): String =
    value.map(_.value.toString).getOrElse("null")

  private def renderOptionalString(value: Option[String]): String =
    value.filter(_.trim.nonEmpty).map(jsonString).getOrElse("null")

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
