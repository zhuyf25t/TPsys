package services.battle.objects.weapon

final case class BattleWeaponSwitchIndex(value: Int) extends AnyVal

object BattleWeaponSwitchIndex {
  /**
   * 中文名：从协议槽位值还原切枪索引（fromWire）。
   * 游戏视线：玩家可以直接选择某个武器槽位，负数没有合法槽位含义，因此会被拒绝为 None。
   * 建模原因：`BattleWeaponSwitchIndex` 是武器槽位索引值对象，用来避免把任意 Int 当成合法槽位；解析失败显式使用 Option 表达。
   */
  def fromWire(value: Int): Option[BattleWeaponSwitchIndex] =
    Option.when(value >= 0)(BattleWeaponSwitchIndex(value))
}
