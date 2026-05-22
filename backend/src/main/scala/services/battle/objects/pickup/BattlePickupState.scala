package services.battle.objects.pickup

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
