package slaydemo.backend.battle.objects.player

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

enum BattleSurvivalOutcome {
  case Survived
  case Eliminated
}

object BattleSurvivalOutcome {
  /**
   * 中文名：从结束存活标记创建（fromAliveAtEnd）。
   * 游戏视线：把结算阶段的 aliveAtEnd 布尔值转换成明确枚举，表示玩家是存活结束还是被淘汰结束。
   */
  def fromAliveAtEnd(aliveAtEnd: Boolean): BattleSurvivalOutcome =
    if aliveAtEnd then BattleSurvivalOutcome.Survived else BattleSurvivalOutcome.Eliminated

  /**
   * 中文名：结束时是否存活（aliveAtEnd）。
   * 游戏视线：把结算结果枚举展开成前端战报需要的布尔值；Survived 表示本局结束时仍然存活。
   */
  def aliveAtEnd(value: BattleSurvivalOutcome): Boolean =
    value == BattleSurvivalOutcome.Survived
}
