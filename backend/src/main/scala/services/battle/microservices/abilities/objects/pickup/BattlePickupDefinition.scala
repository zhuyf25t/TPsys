package services.battle.microservices.abilities.objects.pickup

import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.abilities.objects.pickup.PickupKind
import services.battle.objects.core.BattleVector2

private[services] final case class BattlePickupDefinition(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2
) {
  def initialState: BattlePickupState =
    BattlePickupState(
      pickupId = pickupId,
      pickupKind = pickupKind,
      weaponKind = weaponKind,
      position = position,
      pickupAvailability = BattlePickupAvailability.Available
    )
}
