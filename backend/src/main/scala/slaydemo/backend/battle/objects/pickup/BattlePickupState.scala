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

final case class BattlePickupState(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2,
  pickupAvailability: BattlePickupAvailability
) {
  /**
   * 中文名：是否可拾取（available）。
   * 游戏视线：读取拾取物生命周期的展示字段；true 表示药包或武器现在在地图上，玩家接触后可以触发拾取。
   */
  def available: Boolean =
    BattlePickupAvailability.availableFlag(pickupAvailability)

  /**
   * 中文名：刷新剩余毫秒（respawnMs）。
   * 游戏视线：DurationMillis 是毫秒单位的值对象；在 pickup 状态里表示该拾取物距离重新出现在地图上还剩多久。
   */
  def respawnMs: DurationMillis =
    BattlePickupAvailability.respawnMs(pickupAvailability)
}
