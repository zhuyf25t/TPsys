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

final case class BattleWeaponState(
  weaponKind: WeaponKind,
  ammoInMagazine: AmmoCount,
  magazineSize: AmmoCount,
  reserveAmmo: Option[AmmoCount],
  fireCooldownMs: CooldownMillis,
  reloadRemainingMs: CooldownMillis,
  heat: Int,
  thermalState: BattleWeaponThermalState
) {
  /**
   * 中文名：是否过热（overheated）。
   * 游戏视线：武器开火规则和 HUD 都需要知道当前武器是否因为热量过高而暂时不能射击。
   * 建模原因：真实状态由 `BattleWeaponThermalState` ADT 管理，这里只把它投影成协议/HUD 友好的 Boolean，避免业务层直接维护裸 `overheated` 字段。
   */
  def overheated: Boolean =
    BattleWeaponThermalState.overheatedFlag(thermalState)

  /**
   * 中文名：过热剩余冷却毫秒（overheatRemainingMs）。
   * 游戏视线：玩家看到武器过热后，需要知道距离恢复开火还剩多少毫秒，后端也用它推进热量恢复。
   * 建模原因：`CooldownMillis` 是冷却时间值对象，用来避免直接裸用 Long/Int；在 weapon 里它表示过热锁定的剩余时间。
   */
  def overheatRemainingMs: CooldownMillis =
    BattleWeaponThermalState.overheatRemainingMs(thermalState)
}
