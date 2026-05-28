package services.battle.microservices.runtime.services

import services.battle.database.abilities.BattleSkillRuleBook
import services.battle.microservices.combat.services.BattleWeaponRules
import services.battle.microservices.world.services.{BattleArenaCatalog, BattleArenaCollision, BattleMotionRules}
import services.battle.objects.actors.BattleInputRules.{BattleInputEnvironment, applyCommandToPlayer}
import services.battle.objects.abilities.BattleSkillCommandRules.{
  BattleSkillCommandEnvironment,
  CommandApplication,
  applyBlinkCommand,
  applyDashCommand,
  applyFreezeCommand
}
import services.battle.objects.abilities.{BattleSkillRuleSet, SkillDistance}
import services.battle.objects.{BattleAggregateState, BattleCommandRequest, BattlePlayerState, SkillKind}
import services.battle.objects.core.{BattleVector2, Radius}

private[battle] object BattleCommandApplicationRules {
  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest
  ): CommandApplication = {
    val inputPlayer = applyCommandToPlayer(player, request, battleInputEnvironment)
    val baseApplication = CommandApplication(replacePlayer(state, inputPlayer), Vector.empty)
    val skillEnvironment = battleSkillCommandEnvironment
    val skillApplications = request.skillIntents.values.map {
      case SkillKind.Blink =>
        (currentState: BattleAggregateState) => applyBlinkCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
      case SkillKind.Dash =>
        (currentState: BattleAggregateState) => applyDashCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
      case SkillKind.Freeze =>
        (currentState: BattleAggregateState) => applyFreezeCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
    }

    skillApplications.foldLeft(baseApplication) { case (currentApplication, applySkill) =>
      val applied = applySkill(currentApplication.state)
      CommandApplication(
        state = applied.state,
        outcomes = currentApplication.outcomes ++ applied.outcomes
      )
    }
  }

  private def battleSkillCommandEnvironment: BattleSkillCommandEnvironment =
    BattleSkillCommandEnvironment(
      rules = BattleSkillRuleSet(
        blink = BattleSkillRuleBook.blink,
        dash = BattleSkillRuleBook.dash,
        freeze = BattleSkillRuleBook.freeze
      ),
      playerCollisionRadius = Radius(BattleArenaCatalog.PlayerCollisionRadius),
      isInWorld = point => BattleArenaCollision.isInWorld(point),
      isInWorldWithRadius = (point, radius) => BattleArenaCollision.isInWorld(point, radius.value),
      collidesWithArenaObstacles = (point, radius) => BattleArenaCollision.collidesWithArenaObstacles(point, radius.value),
      isBlockedPoint = point => BattleArenaCollision.isBlockedPoint(point),
      motionDestination = motionDestination
    )

  private def motionDestination(
    position: BattleVector2,
    direction: BattleVector2,
    distance: SkillDistance,
    radius: Radius
  ): BattleVector2 =
    BattleMotionRules.findMotionDestination(
      position = position,
      direction = direction,
      distance = distance.value,
      radius = radius.value
    ).destination

  private def battleInputEnvironment: BattleInputEnvironment =
    BattleInputEnvironment(
      normalizeMovement = BattleMotionRules.normalizeMovement,
      applyWeaponSwitchRequest = BattleWeaponRules.applyWeaponSwitchRequest
    )

  private def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): BattleAggregateState =
    state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing))
}
