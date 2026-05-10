package slaydemo.backend.battle.objects

enum BattleWeaponThermalState {
  case Ready
  case Overheated(remainingMs: CooldownMillis)
}

object BattleWeaponThermalState {
  def overheatedFlag(value: BattleWeaponThermalState): Boolean =
    value match {
      case BattleWeaponThermalState.Overheated(_) => true
      case BattleWeaponThermalState.Ready         => false
    }

  def overheatRemainingMs(value: BattleWeaponThermalState): CooldownMillis =
    value match {
      case BattleWeaponThermalState.Ready =>
        CooldownMillis(0)
      case BattleWeaponThermalState.Overheated(remainingMs) =>
        CooldownMillis(math.max(0, remainingMs.value))
    }

  def overheated(remainingMs: CooldownMillis): BattleWeaponThermalState =
    if remainingMs.value <= 0 then BattleWeaponThermalState.Ready
    else BattleWeaponThermalState.Overheated(CooldownMillis(remainingMs.value))
}
