package services.battle.objects.weapon

import services.battle.objects.*
import services.battle.objects.core.*
import services.battle.objects.event.*
import services.battle.objects.pickup.*
import services.battle.objects.player.*
import services.battle.objects.projectile.*
import services.battle.objects.queue.*
import services.battle.objects.replay.*
import services.battle.objects.result.*
import services.battle.objects.skill.*
import services.battle.objects.weapon.*

enum BattleWeaponSwitchDirection {
  case Previous
  case NoSwitch
  case Next
}

object BattleWeaponSwitchDirection {
  /**
   * 中文名：从协议步进值还原切枪方向（fromWire）。
   * 游戏视线：鼠标滚轮或键盘切枪会传入负数、零或正数，分别表示上一把、不切换、下一把武器。
   * 建模原因：HTTP/前端协议可以使用简单 Int，但领域内部必须转成 `BattleWeaponSwitchDirection` 枚举，避免到处比较魔法数字。
   */
  def fromWire(value: Int): BattleWeaponSwitchDirection =
    if value < 0 then BattleWeaponSwitchDirection.Previous
    else if value > 0 then BattleWeaponSwitchDirection.Next
    else BattleWeaponSwitchDirection.NoSwitch

  /**
   * 中文名：切枪步进值（step）。
   * 游戏视线：把上一把/不切换/下一把转换成 -1/0/1，供武器槽位循环计算和前端协议展示使用。
   * 建模原因：这是枚举到 wire 数字的单一出口，避免不同调用方给 Previous/Next 赋不同数字。
   */
  def step(value: BattleWeaponSwitchDirection): Int =
    value match {
      case BattleWeaponSwitchDirection.Previous => -1
      case BattleWeaponSwitchDirection.NoSwitch => 0
      case BattleWeaponSwitchDirection.Next     => 1
    }
}
