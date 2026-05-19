package slaydemo.backend.battle.objects.replay

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

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BattleReplayHeroFrameState(
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  seat: SeatIndex,
  position: BattleVector2,
  hp: HitPoints,
  maxHp: HitPoints,
  lifeState: BattleReplayHeroLifeState,
  score: Score,
  facing: FacingRadians,
  currentWeaponKind: WeaponKind
) {
  /**
   * 中文名：是否存活（alive）。
   * 游戏视线：回放帧渲染需要快速判断角色当前帧是否还应该以正常英雄形态展示，还是进入淘汰/尸体/隐藏表现。
   * 建模原因：底层使用 `BattleReplayHeroLifeState` 枚举表达生命状态，这里只是导出前端回放协议需要的布尔投影。
   */
  def alive: Boolean =
    BattleReplayHeroLifeState.aliveFlag(lifeState)

  /**
   * 中文名：被淘汰时刻（eliminatedAtMs）。
   * 游戏视线：回放时间轴和击杀提示需要知道角色在战斗开始后第几毫秒被淘汰，用于播放倒地、淡出或结算事件。
   * 建模原因：`ElapsedMillis` 是毫秒单位的值对象，避免直接裸用 Long；Alive 状态固定返回 None，防止活着的角色携带无效淘汰时间。
   */
  def eliminatedAtMs: Option[ElapsedMillis] =
    BattleReplayHeroLifeState.eliminatedAtMs(lifeState)
}

final case class BattleReplayProjectileFrameState(
  projectileId: ProjectileId,
  projectileKind: ProjectileKind,
  position: BattleVector2,
  facing: FacingRadians,
  ttlMs: DurationMillis,
  splashRadius: Radius
)

final case class BattleReplayPickupFrameState(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2,
  pickupAvailability: BattlePickupAvailability
) {
  /**
   * 中文名：是否可拾取（available）。
   * 游戏视线：回放渲染拾取物时，用它决定道具是否显示为可捡、半透明冷却，或者暂时隐藏。
   * 建模原因：拾取物的真实状态由 `BattlePickupAvailability` ADT 管理，这里提供给 JSON/前端的是只读投影。
   */
  def available: Boolean =
    BattlePickupAvailability.availableFlag(pickupAvailability)

  /**
   * 中文名：刷新剩余毫秒（respawnMs）。
   * 游戏视线：拾取物被拿走后，回放和 UI 可以显示它距离再次刷新还剩多少毫秒。
   * 建模原因：`DurationMillis` 是毫秒单位的值对象，用来避免直接裸用 Long；在 pickup 里它表示拾取物刷新倒计时的持续时间/剩余时间。
   */
  def respawnMs: DurationMillis =
    BattlePickupAvailability.respawnMs(pickupAvailability)
}

final case class BattleReplayFrameState(
  elapsedMs: ElapsedMillis,
  heroes: Vector[BattleReplayHeroFrameState],
  projectiles: Vector[BattleReplayProjectileFrameState],
  pickups: Vector[BattleReplayPickupFrameState]
)
