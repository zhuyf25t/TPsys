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

enum BattleReplayHeroLifeState {
  case Alive
  case Eliminated(eliminatedAtMs: Option[ElapsedMillis])
}

object BattleReplayHeroLifeState {
  /**
   * 中文名：从存活标记还原回放生命状态（fromAliveFlag）。
   * 游戏视线：读取旧回放帧或前端协议字段时，把 `alive + eliminatedAtMs` 这组扁平字段重新还原为明确的 Alive/Eliminated 状态。
   * 建模原因：兼容边界可以接收 Boolean，但对象内部用 ADT 表达有限状态，避免“alive=false 但没有淘汰状态对象”的非法组合扩散。
   */
  def fromAliveFlag(alive: Boolean, eliminatedAtMs: Option[ElapsedMillis]): BattleReplayHeroLifeState =
    if alive then BattleReplayHeroLifeState.Alive
    else BattleReplayHeroLifeState.Eliminated(eliminatedAtMs)

  /**
   * 中文名：导出存活标记（aliveFlag）。
   * 游戏视线：把回放生命状态转换为前端帧协议里直观的 `alive` 字段，方便渲染器快速分支。
   * 建模原因：这是 ADT 到协议字段的单一出口，避免调用方手写 match 后产生不同的布尔解释。
   */
  def aliveFlag(value: BattleReplayHeroLifeState): Boolean =
    value == BattleReplayHeroLifeState.Alive

  /**
   * 中文名：读取淘汰时刻（eliminatedAtMs）。
   * 游戏视线：回放事件流需要知道角色在哪个 elapsedMs 被淘汰，用来定位击杀提示和死亡表现。
   * 建模原因：Alive 状态返回 None，Eliminated 状态返回它携带的 `ElapsedMillis`，保证淘汰时间只属于淘汰状态。
   */
  def eliminatedAtMs(value: BattleReplayHeroLifeState): Option[ElapsedMillis] =
    value match {
      case BattleReplayHeroLifeState.Alive =>
        None
      case BattleReplayHeroLifeState.Eliminated(eliminatedAtMs) =>
        eliminatedAtMs
    }
}
