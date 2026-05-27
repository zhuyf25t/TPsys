package services.battle.database.session

import java.util.UUID

import services.battle.objects.core.BattleId

private[services] trait BattleIdGenerator {
  /** 中文名：next战斗标识（nextBattleId）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  def nextBattleId(): BattleId
}

private[services] object RandomBattleIdGenerator extends BattleIdGenerator {
  /** 中文名：next战斗标识（nextBattleId）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  override def nextBattleId(): BattleId =
    BattleId(s"battle-${UUID.randomUUID().toString}")
}
