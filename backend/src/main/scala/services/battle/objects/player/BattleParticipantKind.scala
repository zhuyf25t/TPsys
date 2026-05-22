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
