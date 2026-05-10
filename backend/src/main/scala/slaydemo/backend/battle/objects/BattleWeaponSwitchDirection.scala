package slaydemo.backend.battle.objects

enum BattleWeaponSwitchDirection {
  case Previous
  case NoSwitch
  case Next
}

object BattleWeaponSwitchDirection {
  def fromWire(value: Int): BattleWeaponSwitchDirection =
    if value < 0 then BattleWeaponSwitchDirection.Previous
    else if value > 0 then BattleWeaponSwitchDirection.Next
    else BattleWeaponSwitchDirection.NoSwitch

  def step(value: BattleWeaponSwitchDirection): Int =
    value match {
      case BattleWeaponSwitchDirection.Previous => -1
      case BattleWeaponSwitchDirection.NoSwitch => 0
      case BattleWeaponSwitchDirection.Next     => 1
    }
}
