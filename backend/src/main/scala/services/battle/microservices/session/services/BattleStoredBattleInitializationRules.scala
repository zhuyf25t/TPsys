package services.battle.microservices.session.services

import services.battle.objects.core.{DurationMillis, EpochMillis}
import services.battle.objects.result.BattleFinishProjectionStatus

private[battle] object BattleStoredBattleInitializationRules {
  /** 中文名：从seed（fromSeed）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def fromSeed(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis
  ): StoredBattle = {
    val initialState = BattleSessionStateFactory.createInitialState(seed, battleDuration, now)
    StoredBattle(
      state = initialState,
      commandOwnershipByPlayerId = seed.commandOwnership.map(entry => entry.playerId -> entry.ticketId).toMap,
      finishProjectionStatus = BattleFinishProjectionStatus.Pending,
      lastUpdatedAt = initialState.serverTime,
      pendingStepMs = 0L
    )
  }
}
