package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.microservices.combat.services.BattleWeaponRules
import services.battle.microservices.world.services.{BattleArenaCatalog, BattleArenaCollision, BattleMotionRules}
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.actors.services.BattleInputRules.{BattleInputEnvironment, applyCommandToPlayer}
import services.battle.microservices.abilities.services.BattleSkillCommandRules.{
  BattleSkillCommandEnvironment,
  CommandApplication,
  applyBlinkCommand,
  applyCriticalCommand,
  applyDashCommand,
  applyFreezeCommand
}
import services.battle.microservices.abilities.objects.abilities.{BattleSkillRuleSet, SkillDistance}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.objects.BattleAggregateState
import services.battle.objects.core.{BattleVector2, Radius}
import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.microservices.runtime.objects.command.BattleCommandRequest

private[battle] object BattleCommandApplicationRules {
  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest,
    battleRules: BattleDynamicRuleBook
  ): IO[CommandApplication] =
    for
      arena <- BattleArenaCatalog.contextFor(state.mapId, battleRules)
      inputEnvironment <- battleInputEnvironment(battleRules)
      inputPlayer <- applyCommandToPlayer(player, request, inputEnvironment)
      skillEnvironment <- battleSkillCommandEnvironment(arena, battleRules)
      baseState <- replacePlayer(state, inputPlayer)
      application <- {
      val baseApplication = CommandApplication(baseState, Vector.empty)
      val skillApplications = request.skillIntents.values.map {
        case SkillKind.Blink =>
          (currentState: BattleAggregateState) => applyBlinkCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
        case SkillKind.Dash =>
          (currentState: BattleAggregateState) => applyDashCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
        case SkillKind.Freeze =>
          (currentState: BattleAggregateState) => applyFreezeCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
        case SkillKind.Critical =>
          (currentState: BattleAggregateState) => applyCriticalCommand(currentState, inputPlayer.playerId, request, skillEnvironment)
      }

      skillApplications.foldLeft(IO.pure(baseApplication)) { case (currentApplicationIO, applySkill) =>
        currentApplicationIO.flatMap { currentApplication =>
          applySkill(currentApplication.state).map { applied =>
            CommandApplication(
              state = applied.state,
              outcomes = currentApplication.outcomes ++ applied.outcomes
            )
          }
        }
      }
      }
    yield application

  private def battleSkillCommandEnvironment(
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleSkillCommandEnvironment] =
    for
      blink <- battleRules.blink
      dash <- battleRules.dash
      freeze <- battleRules.freeze
      critical <- battleRules.critical
    yield BattleSkillCommandEnvironment(
      rules = BattleSkillRuleSet(blink = blink, dash = dash, freeze = freeze, critical = critical),
      playerCollisionRadius = Radius(arena.playerCollisionRadius),
      isInWorld = point => BattleArenaCollision.isInWorld(point, arena),
      isInWorldWithRadius = (point, radius) => BattleArenaCollision.isInWorld(point, radius.value, arena),
      collidesWithArenaObstacles = (point, radius) => BattleArenaCollision.collidesWithArenaObstacles(point, radius.value, arena),
      isBlockedPoint = point => BattleArenaCollision.isBlockedPoint(point, arena),
      motionDestination = (position, direction, distance, radius) => motionDestination(position, direction, distance, radius, arena)
    )

  private def motionDestination(
    position: BattleVector2,
    direction: BattleVector2,
    distance: SkillDistance,
    radius: Radius,
    arena: BattleArenaContext
  ): IO[BattleVector2] =
    BattleMotionRules.findMotionDestination(
      position = position,
      direction = direction,
      distance = distance.value,
      radius = radius.value,
      arena = arena
    ).map(_.destination)

  private def battleInputEnvironment(battleRules: BattleDynamicRuleBook): IO[BattleInputEnvironment] =
    IO.pure(BattleInputEnvironment(
      normalizeMovement = BattleMotionRules.normalizeMovement,
      applyWeaponSwitchRequest = (player, direction, requestedIndex) =>
        BattleWeaponRules.applyWeaponSwitchRequest(player, direction, requestedIndex, battleRules)
    ))

  private def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): IO[BattleAggregateState] =
    IO.pure(state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing)))
}
