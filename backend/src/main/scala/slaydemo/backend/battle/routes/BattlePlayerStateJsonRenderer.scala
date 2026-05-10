package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.routes.BattleStateJsonSupport.*

private[routes] object BattlePlayerStateJsonRenderer {
  def renderPlayer(player: BattlePlayerState): String = {
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
}
