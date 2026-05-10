package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.routes.BattleStateJsonSupport.*

private[routes] object BattleEntityStateJsonRenderer {
  def renderProjectile(projectile: BattleProjectileState): String =
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

  def renderProjectileTerminal(terminal: BattleProjectileTerminalState): String =
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

  def renderSlowField(field: BattleSlowFieldState): String =
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

  def renderPickup(pickup: BattlePickupState): String =
    renderObject(
      Vector(
        "pickupId" -> jsonString(pickup.pickupId.value),
        "kind" -> jsonString(PickupKind.wireValue(pickup.pickupKind)),
        "position" -> renderVector(pickup.position),
        "available" -> pickup.available.toString,
        "respawnMs" -> pickup.respawnMs.value.toString
      ) ++ optionalStringField("weaponKind", pickup.weaponKind.map(WeaponKind.wireValue))
    )

  def renderEvent(event: BattleEventState): String = {
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
}
