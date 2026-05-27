package services.battle.database.runtime

import services.battle.database.abilities.BattleSkillCommandRules.{
  CommandApplication,
  applyBlinkCommand,
  applyDashCommand,
  applyFreezeCommand
}
import services.battle.database.actors.BattleInputRules.applyCommandToPlayer
import services.battle.database.runtime.BattleAggregateUpdateRules.replacePlayer
import services.battle.objects.{BattleAggregateState, BattleCommandRequest, BattlePlayerState, SkillKind}

private[battle] object BattleCommandApplicationRules {
  /** 中文名：应用命令（applyCommand）。游戏职责：把客户端输入应用到玩家状态，并按技能意图触发对应能力规则。 */
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
