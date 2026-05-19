package slaydemo.backend.battle.services.session

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.api.BattleCommandRequest
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.runtime.BattleAggregateUpdateRules.replacePlayer
import slaydemo.backend.battle.services.actors.BattleInputRules.applyCommandToPlayer
import slaydemo.backend.battle.services.abilities.BattleSkillCommandRules.{
  CommandApplication,
  applyBlinkCommand,
  applyDashCommand,
  applyFreezeCommand
}

private[services] object BattleCommandApplicationRules {
  /** 中文名：应用命令（applyCommand）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest
  ): CommandApplication = {
    val inputPlayer = applyCommandToPlayer(player, request)
    val baseApplication = CommandApplication(replacePlayer(state, inputPlayer), Vector.empty)
    val skillApplications = request.skillIntents.values.map {
      case SkillKind.Blink =>
        (currentState: BattleAggregateState) => applyBlinkCommand(currentState, inputPlayer.playerId, request)
      case SkillKind.Dash =>
        (currentState: BattleAggregateState) => applyDashCommand(currentState, inputPlayer.playerId, request)
      case SkillKind.Freeze =>
        (currentState: BattleAggregateState) => applyFreezeCommand(currentState, inputPlayer.playerId, request)
    }

    skillApplications.foldLeft(baseApplication) { case (currentApplication, applySkill) =>
      val applied = applySkill(currentApplication.state)
      CommandApplication(
        state = applied.state,
        outcomes = currentApplication.outcomes ++ applied.outcomes
      )
    }
  }
}
