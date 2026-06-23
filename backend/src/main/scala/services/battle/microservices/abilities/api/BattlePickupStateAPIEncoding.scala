package services.battle.microservices.abilities.api

import io.circe.Encoder
import services.battle.microservices.abilities.objects.pickup.{BattlePickupState, PickupKind}
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.world.api.BattleVectorAPIEncoding.given

object BattlePickupStateAPIEncoding {
  given Encoder[BattlePickupState] =
    Encoder
      .forProduct6("pickupId", "kind", "position", "available", "respawnMs", "weaponKind")(
        (response: BattlePickupState) =>
          (
            response.pickupId.value,
            PickupKind.wireValue(response.pickupKind),
            response.position,
            response.available,
            response.respawnMs.value,
            response.weaponKind.map(WeaponKind.wireValue)
          )
      )
      .mapJson(_.dropNullValues)
}
