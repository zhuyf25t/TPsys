package services.battle.application

import services.battle.application.*

import services.battle.objects.*
import services.battle.engine.BattleInputRules.lastClientCommandSeq

private[services] object BattleCommandAcceptanceFactory {
  /** 中文名：ignored（ignored）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  def ignored(
    state: BattleAggregateState,
    player: BattlePlayerState,
    serverTime: EpochMillis
  ): BattleCommandAccepted =
    BattleCommandAccepted(
      battleId = state.battleId,
      acceptedTick = state.tick,
      acceptedCommandSeq = player.lastClientCommandSeq,
      serverTime = serverTime,
      commandStatus = BattleCommandStatus.Ignored,
      commandReason = Some(ignoredReason(state, player)),
      outcomes = Vector.empty
    )

  /** 中文名：applied（applied）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  def applied(
    state: BattleAggregateState,
    playerId: PlayerId,
    serverTime: EpochMillis,
    outcomes: Vector[BattleCommandSkillOutcome]
  ): BattleCommandAccepted =
    BattleCommandAccepted(
      battleId = state.battleId,
      acceptedTick = state.tick,
      acceptedCommandSeq = lastClientCommandSeq(state, playerId),
      serverTime = serverTime,
      commandStatus = BattleCommandStatus.Applied,
      commandReason = None,
      outcomes = outcomes
    )

  private def ignoredReason(state: BattleAggregateState, player: BattlePlayerState): BattleCommandReason =
    if !player.alive then BattleCommandReason.PlayerDead
    else if state.phase == BattlePhase.Finished then BattleCommandReason.BattleFinished
    else BattleCommandReason.BattleInactive
}
