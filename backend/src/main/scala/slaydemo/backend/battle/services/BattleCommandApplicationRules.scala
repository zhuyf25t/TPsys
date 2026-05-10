package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.BattleCommandRequest
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleAggregateUpdateRules.replacePlayer
import slaydemo.backend.battle.services.BattleInputRules.applyCommandToPlayer
import slaydemo.backend.battle.services.BattleSkillCommandRules.{
  CommandApplication,
  applyBlinkCommand,
  applyDashCommand,
  applyFreezeCommand
}

private[services] object BattleCommandApplicationRules {
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
