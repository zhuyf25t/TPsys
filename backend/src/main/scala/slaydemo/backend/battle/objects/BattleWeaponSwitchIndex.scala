package slaydemo.backend.battle.objects

final case class BattleWeaponSwitchIndex(value: Int) extends AnyVal

object BattleWeaponSwitchIndex {
  def fromWire(value: Int): Option[BattleWeaponSwitchIndex] =
    Option.when(value >= 0)(BattleWeaponSwitchIndex(value))
}
