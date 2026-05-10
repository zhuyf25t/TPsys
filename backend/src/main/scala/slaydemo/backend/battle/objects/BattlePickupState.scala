package slaydemo.backend.battle.objects

final case class BattlePickupState(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2,
  pickupAvailability: BattlePickupAvailability
) {
  def available: Boolean =
    BattlePickupAvailability.availableFlag(pickupAvailability)

  def respawnMs: DurationMillis =
    BattlePickupAvailability.respawnMs(pickupAvailability)
}
