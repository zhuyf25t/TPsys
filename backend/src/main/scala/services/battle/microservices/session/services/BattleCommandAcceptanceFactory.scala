package services.battle.microservices.session.services

import cats.effect.IO

import services.battle.microservices.runtime.services.BattleEngine
import services.battle.objects.BattlePhase
import services.battle.microservices.session.objects.command.{
  BattleCommandAccepted,
  BattleCommandReason,
  BattleCommandSkillOutcome,
  BattleCommandStatus
}
import services.battle.objects.core.{BattleAggregateState, EpochMillis, PlayerId}
import services.battle.microservices.actors.objects.player.BattlePlayerState

private[battle] object BattleCommandAcceptanceFactory {
  /** 中文名：ignored（ignored）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def ignored(
    state: BattleAggregateState,
    player: BattlePlayerState,
    serverTime: EpochMillis
  ): IO[BattleCommandAccepted] =
    ignoredReason(state, player).map { reason =>
      BattleCommandAccepted(
        battleId = state.battleId,
        acceptedTick = state.tick,
        acceptedCommandSeq = player.lastClientCommandSeq,
        serverTime = serverTime,
        commandStatus = BattleCommandStatus.Ignored,
        commandReason = Some(reason),
        outcomes = Vector.empty
      )
    }

  /** 中文名：applied（applied）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def applied(
    state: BattleAggregateState,
    playerId: PlayerId,
    serverTime: EpochMillis,
    outcomes: Vector[BattleCommandSkillOutcome]
  ): IO[BattleCommandAccepted] =
    BattleEngine.lastClientCommandSeq(state, playerId).map { acceptedCommandSeq =>
      BattleCommandAccepted(
        battleId = state.battleId,
        acceptedTick = state.tick,
        acceptedCommandSeq = acceptedCommandSeq,
        serverTime = serverTime,
        commandStatus = BattleCommandStatus.Applied,
        commandReason = None,
        outcomes = outcomes
      )
    }

  private def ignoredReason(state: BattleAggregateState, player: BattlePlayerState): IO[BattleCommandReason] =
    IO.pure {
      if !player.alive then BattleCommandReason.PlayerDead
      else if state.phase == BattlePhase.Finished then BattleCommandReason.BattleFinished
      else BattleCommandReason.BattleInactive
    }
}
