package services.battle.microservices.combat.api

import io.circe.Encoder
import services.battle.microservices.combat.objects.weapon.{BattleWeaponState, WeaponKind}

object BattleWeaponStateAPIEncoding {
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
