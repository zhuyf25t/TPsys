package services.battle.objects.apiTypes.state

import io.circe.{Encoder, Json}
import io.circe.syntax.*
import services.battle.objects.{SkillKind, WeaponKind}
import services.battle.objects.core.{
  AmmoCount,
  CooldownMillis,
  BattleWeaponHeat
}
import services.battle.objects.player.{BattlePlayerSkillState, BattlePlayerState}
import services.battle.objects.weapon.BattleWeaponState

import BattleStateVectorResponse.given

object BattleStateWeaponResponse {
  given Encoder[BattleWeaponState] =
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
    )((response: BattleWeaponState) =>
        (
        WeaponKind.wireValue(response.weaponKind),
        response.ammoInMagazine.value,
        response.magazineSize.value,
        response.reserveAmmo.map(_.value),
        response.fireCooldownMs.value,
        response.reloadRemainingMs.value,
        response.heat.value,
        response.overheated,
        response.overheatRemainingMs.value
      )
    )
}

object BattleStateSkillResponse {
  given Encoder[BattlePlayerSkillState] =
    Encoder.forProduct3("kind", "cooldownMs", "activeMs")((response: BattlePlayerSkillState) =>
      (SkillKind.wireValue(response.skillKind), response.cooldownMs.value, response.activeMs.value)
    )
}

object BattleStatePlayerResponse {
  import BattleStateWeaponResponse.given
  import BattleStateSkillResponse.given

  given Encoder[BattlePlayerState] =
    Encoder.instance(player =>
      val currentWeapon = player.weapons.lift(player.currentWeaponIndex)
      Json.obj(
        "playerId" -> player.playerId.value.asJson,
        "heroId" -> player.heroId.value.asJson,
        "handle" -> player.handle.value.asJson,
        "displayName" -> player.displayName.value.asJson,
        "seat" -> player.seat.value.asJson,
        "isBot" -> player.isBot.asJson,
        "position" -> player.position.asJson,
        "aim" -> player.aim.asJson,
        "facing" -> player.facing.value.asJson,
        "movement" -> player.movement.asJson,
        "sprint" -> player.sprint.asJson,
        "primaryHeld" -> player.primaryHeld.asJson,
        "reloadPressed" -> player.reloadPressed.asJson,
        "lastClientCommandSeq" -> player.lastClientCommandSeq.value.asJson,
        "currentWeaponIndex" -> player.currentWeaponIndex.asJson,
        "weapons" -> player.weapons.asJson,
        "currentWeaponKind" -> WeaponKind.wireValue(player.currentWeaponKind).asJson,
        "ammoInMagazine" -> currentWeapon.map(_.ammoInMagazine).getOrElse(AmmoCount(0)).value.asJson,
        "magazineSize" -> currentWeapon.map(_.magazineSize).getOrElse(AmmoCount(0)).value.asJson,
        "reserveAmmo" -> currentWeapon.flatMap(_.reserveAmmo).map(_.value).asJson,
        "fireCooldownMs" -> currentWeapon.map(_.fireCooldownMs).getOrElse(CooldownMillis(0)).value.asJson,
        "reloadRemainingMs" -> currentWeapon.map(_.reloadRemainingMs).getOrElse(CooldownMillis(0)).value.asJson,
        "heat" -> currentWeapon.map(_.heat).getOrElse(BattleWeaponHeat(0)).value.asJson,
        "overheated" -> currentWeapon.map(_.overheated).getOrElse(false).asJson,
        "overheatRemainingMs" -> currentWeapon.map(_.overheatRemainingMs).getOrElse(CooldownMillis(0)).value.asJson,
        "hp" -> player.hp.value.asJson,
        "maxHp" -> player.maxHp.value.asJson,
        "stamina" -> player.stamina.value.asJson,
        "maxStamina" -> player.maxStamina.value.asJson,
        "score" -> player.score.value.asJson,
        "kills" -> player.kills.value.asJson,
        "skills" -> player.skills.asJson,
        "alive" -> player.alive.asJson,
        "eliminatedAtMs" -> player.eliminatedAtMs.map(_.value).asJson,
        "respawnMs" -> player.respawnMs.value.asJson
      )
    )
}
