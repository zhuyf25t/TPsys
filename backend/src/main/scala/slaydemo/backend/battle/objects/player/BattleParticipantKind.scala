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

enum BattleParticipantKind {
  case Human
  case Bot
}

object BattleParticipantKind {
  /**
   * 中文名：从机器人标记创建（fromBotFlag）。
   * 游戏视线：把旧接口或队列 bootstrap 里的 isBot 布尔值转换成明确枚举，避免用 Boolean 隐藏 Human/Bot 业务身份。
   */
  def fromBotFlag(isBot: Boolean): BattleParticipantKind =
    if isBot then BattleParticipantKind.Bot else BattleParticipantKind.Human

  /**
   * 中文名：判断是否机器人（isBot）。
   * 游戏视线：把参与者枚举展开成前端或规则层需要的布尔值；Bot 表示由服务端 AI 控制，不是玩家手动输入。
   */
  def isBot(value: BattleParticipantKind): Boolean =
    value == BattleParticipantKind.Bot
}
