package slaydemo.backend.battle.objects

final case class BattleWeaponState(
  weaponKind: WeaponKind,
  ammoInMagazine: AmmoCount,
  magazineSize: AmmoCount,
  reserveAmmo: Option[AmmoCount],
  fireCooldownMs: CooldownMillis,
  reloadRemainingMs: CooldownMillis,
  heat: Int,
  thermalState: BattleWeaponThermalState
) {
  def overheated: Boolean =
    BattleWeaponThermalState.overheatedFlag(thermalState)

  def overheatRemainingMs: CooldownMillis =
    BattleWeaponThermalState.overheatRemainingMs(thermalState)
}
