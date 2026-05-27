package services.battle.objects.pickup

import services.battle.objects.{PickupKind, WeaponKind}
import services.battle.objects.core.{BattleVector2, PickupId}

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
