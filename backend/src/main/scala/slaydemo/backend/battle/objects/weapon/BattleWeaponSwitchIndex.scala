package slaydemo.backend.battle.objects.weapon

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.objects.core.*
import slaydemo.backend.battle.objects.event.*
import slaydemo.backend.battle.objects.pickup.*
import slaydemo.backend.battle.objects.player.*
import slaydemo.backend.battle.objects.projectile.*
import slaydemo.backend.battle.objects.queue.*
import slaydemo.backend.battle.objects.replay.*
import slaydemo.backend.battle.objects.result.*
import slaydemo.backend.battle.objects.skill.*
import slaydemo.backend.battle.objects.weapon.*

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
