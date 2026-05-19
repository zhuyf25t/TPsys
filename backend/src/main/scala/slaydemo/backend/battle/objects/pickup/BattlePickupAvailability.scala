package slaydemo.backend.battle.objects.pickup

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

enum BattlePickupAvailability {
  case Available
  case Respawning(remainingMs: DurationMillis)
}

object BattlePickupAvailability {
  /**
   * 中文名：可拾取标记（availableFlag）。
   * 游戏视线：把拾取物刷新生命周期折叠成前端需要的布尔值；Available 表示地图上可见且可以被玩家捡起。
   */
  def availableFlag(value: BattlePickupAvailability): Boolean =
    value == BattlePickupAvailability.Available

  /**
   * 中文名：刷新剩余毫秒（respawnMs）。
   * 游戏视线：DurationMillis 是毫秒单位的值对象；在 pickup 里表示拾取物被捡走后重新刷出的剩余倒计时。
   */
  def respawnMs(value: BattlePickupAvailability): DurationMillis =
    value match {
      case BattlePickupAvailability.Available =>
        DurationMillis(0L)
      case BattlePickupAvailability.Respawning(remainingMs) =>
        DurationMillis(math.max(0L, remainingMs.value))
    }

  /**
   * 中文名：创建刷新中状态（respawning）。
   * 游戏视线：根据剩余毫秒创建拾取物生命周期；倒计时小于等于 0 时直接回到 Available，避免出现“已刷新但仍不可捡”的非法状态。
   */
  def respawning(remainingMs: DurationMillis): BattlePickupAvailability =
    if remainingMs.value <= 0L then BattlePickupAvailability.Available
    else BattlePickupAvailability.Respawning(DurationMillis(remainingMs.value))

  /**
   * 中文名：从展开字段还原（fromAvailableFlag）。
   * 游戏视线：把 JSON 中的 available 和 respawnMs 两个字段反序列化回 ADT，重新约束拾取物可用性生命周期。
   */
  def fromAvailableFlag(available: Boolean, respawnMs: DurationMillis): BattlePickupAvailability =
    if available then BattlePickupAvailability.Available
    else BattlePickupAvailability.Respawning(DurationMillis(math.max(0L, respawnMs.value)))
}
