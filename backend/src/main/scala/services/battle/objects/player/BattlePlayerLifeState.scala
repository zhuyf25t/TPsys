package services.battle.objects.player

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

enum BattlePlayerLifeState {
  case Alive
  case Eliminated(eliminatedAtMs: Option[ElapsedMillis], respawnMs: DurationMillis)
}

object BattlePlayerLifeState {
  /**
   * 中文名：从存活标记创建（fromAliveFlag）。
   * 游戏视线：把 JSON 中的 alive、eliminatedAtMs、respawnMs 展开字段还原成生命状态 ADT，避免活着却带复活倒计时的非法组合。
   */
  def fromAliveFlag(
    alive: Boolean,
    eliminatedAtMs: Option[ElapsedMillis],
    respawnMs: DurationMillis
  ): BattlePlayerLifeState =
    if alive then BattlePlayerLifeState.Alive
    else BattlePlayerLifeState.Eliminated(eliminatedAtMs, DurationMillis(math.max(0L, respawnMs.value)))

  /**
   * 中文名：创建淘汰状态（eliminated）。
   * 游戏视线：用于玩家被击败时构造生命状态；DurationMillis 是毫秒单位值对象，在这里表示复活倒计时剩余时间。
   */
  def eliminated(eliminatedAtMs: Option[ElapsedMillis], respawnMs: DurationMillis): BattlePlayerLifeState =
    BattlePlayerLifeState.Eliminated(eliminatedAtMs, DurationMillis(math.max(0L, respawnMs.value)))

  /**
   * 中文名：更新复活倒计时（withRespawnMs）。
   * 游戏视线：只会修改 Eliminated 状态里的倒计时；Alive 玩家保持 Alive，避免活着的玩家携带复活时间。
   */
  def withRespawnMs(value: BattlePlayerLifeState, respawnMs: DurationMillis): BattlePlayerLifeState =
    value match {
      case BattlePlayerLifeState.Alive =>
        BattlePlayerLifeState.Alive
      case BattlePlayerLifeState.Eliminated(eliminatedAtMs, _) =>
        eliminated(eliminatedAtMs, respawnMs)
    }

  /**
   * 中文名：存活标记（aliveFlag）。
   * 游戏视线：把生命状态 ADT 展开成前端 HUD 需要的 alive 布尔值；只有 Alive 会返回 true。
   */
  def aliveFlag(value: BattlePlayerLifeState): Boolean =
    value == BattlePlayerLifeState.Alive

  /**
   * 中文名：淘汰时刻毫秒（eliminatedAtMs）。
   * 游戏视线：ElapsedMillis 是战斗开始后的毫秒时间点；该函数只在玩家已淘汰时返回淘汰发生时间。
   */
  def eliminatedAtMs(value: BattlePlayerLifeState): Option[ElapsedMillis] =
    value match {
      case BattlePlayerLifeState.Alive =>
        None
      case BattlePlayerLifeState.Eliminated(eliminatedAtMs, _) =>
        eliminatedAtMs
    }

  /**
   * 中文名：复活剩余毫秒（respawnMs）。
   * 游戏视线：DurationMillis 是毫秒单位值对象；在玩家生命状态里表示被淘汰后距离重新加入战斗还剩多久。
   */
  def respawnMs(value: BattlePlayerLifeState): DurationMillis =
    value match {
      case BattlePlayerLifeState.Alive =>
        DurationMillis(0L)
      case BattlePlayerLifeState.Eliminated(_, respawnMs) =>
        DurationMillis(math.max(0L, respawnMs.value))
    }
}
