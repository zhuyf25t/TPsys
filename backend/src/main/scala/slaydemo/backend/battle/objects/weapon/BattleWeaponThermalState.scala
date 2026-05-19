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

enum BattleWeaponThermalState {
  case Ready
  case Overheated(remainingMs: CooldownMillis)
}

object BattleWeaponThermalState {
  /**
   * 中文名：是否过热标记（overheatedFlag）。
   * 游戏视线：把武器热状态转换成 HUD 和开火判断能直接读取的 `overheated` 字段。
   * 建模原因：过热是有限状态，内部用 `Ready/Overheated` 表达；Boolean 只作为边界投影，避免非法组合散落在业务代码里。
   */
  def overheatedFlag(value: BattleWeaponThermalState): Boolean =
    value match {
      case BattleWeaponThermalState.Overheated(_) => true
      case BattleWeaponThermalState.Ready         => false
    }

  /**
   * 中文名：读取过热剩余冷却毫秒（overheatRemainingMs）。
   * 游戏视线：武器处于 Ready 时剩余冷却为 0；处于 Overheated 时返回还要等待多久才能再次开火。
   * 建模原因：`CooldownMillis` 是冷却时间值对象，并在这里集中做非负归一化，避免前端或规则层看到负数冷却。
   */
  def overheatRemainingMs(value: BattleWeaponThermalState): CooldownMillis =
    value match {
      case BattleWeaponThermalState.Ready =>
        CooldownMillis(0)
      case BattleWeaponThermalState.Overheated(remainingMs) =>
        CooldownMillis(math.max(0, remainingMs.value))
    }

  /**
   * 中文名：构造过热状态（overheated）。
   * 游戏视线：开火导致热量爆表时，规则层用这个函数生成带剩余冷却时间的过热状态；冷却时间小于等于 0 时直接视为 Ready。
   * 建模原因：把“非正冷却不能代表过热”的约束集中在构造函数里，防止产生 `Overheated(0ms)` 这种临界非法状态。
   */
  def overheated(remainingMs: CooldownMillis): BattleWeaponThermalState =
    if remainingMs.value <= 0 then BattleWeaponThermalState.Ready
    else BattleWeaponThermalState.Overheated(CooldownMillis(remainingMs.value))
}
