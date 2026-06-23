package services.battle.microservices.abilities.services

import cats.effect.IO

import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.abilities.objects.abilities.{BattleSkillRuleSet, BattleSkillRuntime, SkillDistance}
import services.battle.microservices.abilities.services.BattleSkillRules.availabilityFailure
import services.battle.microservices.abilities.objects.skill.{
  BattleCommandSkillOutcome,
  SkillKind,
  SkillOutcomeReason,
  SkillOutcomeStatus,
  SlowFieldId
}
import services.battle.microservices.runtime.objects.command.BattleCommandRequest
import services.battle.objects.core.{BattleAggregateState, BattleVector2, PlayerId, Radius}
import services.battle.microservices.actors.objects.player.{BattlePlayerSkillState, BattlePlayerState}
import services.battle.microservices.abilities.objects.skill.BattleSlowFieldState

private[battle] object BattleSkillCommandRules {
  final case class CommandApplication(
    state: BattleAggregateState,
    outcomes: Vector[BattleCommandSkillOutcome]
  )

  final case class BattleSkillCommandEnvironment(
    rules: BattleSkillRuleSet,
    playerCollisionRadius: Radius,
    isInWorld: BattleVector2 => IO[Boolean],
    isInWorldWithRadius: (BattleVector2, Radius) => IO[Boolean],
    collidesWithArenaObstacles: (BattleVector2, Radius) => IO[Boolean],
    isBlockedPoint: BattleVector2 => IO[Boolean],
    motionDestination: (BattleVector2, BattleVector2, SkillDistance, Radius) => IO[BattleVector2]
  )

  def applyBlinkCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): IO[CommandApplication] =
    withAvailableSkill(state, playerId, SkillKind.Blink) { player =>
      request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
        case None =>
          commandApplication(state, SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))
        case Some(target) =>
          for
            inWorld <- environment.isInWorldWithRadius(target, environment.playerCollisionRadius)
            collides <-
              if inWorld then environment.collidesWithArenaObstacles(target, environment.playerCollisionRadius)
              else IO.pure(false)
            application <-
              if !inWorld then
                commandApplication(state, SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))
              else if collides then
                commandApplication(state, SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))
              else
                for
                  targetAllowed <- isBlinkTargetAllowed(player.position, target, environment)
                  nextApplication <-
                    if !targetAllowed then
                      commandApplication(state, SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))
                    else
                      for
                        destination <- blinkDestination(target)
                        blocked <- environment.isBlockedPoint(destination)
                        result <-
                          if blocked then
                            commandApplication(state, SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))
                          else
                            spendSkillStamina(state, player, SkillKind.Blink) { chargedPlayer =>
                              for
                                updatedSkills <- updateSkill(chargedPlayer.skills, SkillKind.Blink, environment.rules.blink.runtime)
                                movedPlayer = chargedPlayer.copy(position = destination, skills = updatedSkills)
                                nextState <- replacePlayer(state, movedPlayer)
                                applied <- commandApplication(nextState, SkillKind.Blink, SkillOutcomeStatus.Applied, None)
                              yield applied
                            }
                      yield result
                yield nextApplication
          yield application
      }
    }

  def applyDashCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): IO[CommandApplication] =
    withAvailableSkill(state, playerId, SkillKind.Dash) { player =>
      for
        direction <- normalizedDirection(BattleVector2(request.movement.x, request.movement.y))
        directionLength <- vectorLength(direction)
        dashDirection = if directionLength > 0.0 then direction else player.aim
        dashDirectionLength <- vectorLength(dashDirection)
        application <-
          if dashDirectionLength <= 0.0 then
            commandApplication(state, SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.NoDirection))
          else
            for
              destination <- environment.motionDestination(
                player.position,
                dashDirection,
                environment.rules.dash.distance,
                environment.playerCollisionRadius
              )
              traveled <- distanceBetween(player.position, destination)
              nextApplication <-
                if traveled <= 0.001 then
                  commandApplication(state, SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))
                else
                  spendSkillStamina(state, player, SkillKind.Dash) { chargedPlayer =>
                    for
                      updatedSkills <- updateSkill(chargedPlayer.skills, SkillKind.Dash, environment.rules.dash.runtime)
                      movedPlayer = chargedPlayer.copy(position = destination, skills = updatedSkills)
                      nextState <- replacePlayer(state, movedPlayer)
                      applied <- commandApplication(nextState, SkillKind.Dash, SkillOutcomeStatus.Applied, None)
                    yield applied
                  }
            yield nextApplication
      yield application
    }

  def applyFreezeCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): IO[CommandApplication] =
    withAvailableSkill(state, playerId, SkillKind.Freeze) { player =>
      request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
        case None =>
          commandApplication(state, SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))
        case Some(target) =>
          for
            inWorld <- environment.isInWorld(target)
            targetDistance <- distanceBetween(player.position, target)
            application <-
              if !inWorld then
                commandApplication(state, SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))
              else if targetDistance > environment.rules.freeze.castRange.value then
                commandApplication(state, SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))
              else
                val field = BattleSlowFieldState(
                  fieldId = SlowFieldId(s"slow-${player.playerId.value}-${request.clientCommandSeq.value}"),
                  ownerPlayerId = player.playerId,
                  ownerHeroId = player.heroId,
                  position = target,
                  radius = environment.rules.freeze.radius,
                  ttlMs = environment.rules.freeze.runtime.activeMs,
                  durationMs = environment.rules.freeze.runtime.activeMs
                )
                spendSkillStamina(state, player, SkillKind.Freeze) { chargedPlayer =>
                  for
                    updatedSkills <- updateSkill(chargedPlayer.skills, SkillKind.Freeze, environment.rules.freeze.runtime)
                    updatedPlayer = chargedPlayer.copy(skills = updatedSkills)
                    stateWithPlayer <- replacePlayer(state, updatedPlayer)
                    nextState = stateWithPlayer.copy(slowFields = stateWithPlayer.slowFields :+ field)
                    applied <- commandApplication(nextState, SkillKind.Freeze, SkillOutcomeStatus.Applied, None)
                  yield applied
                }
          yield application
      }
    }

  def applyCriticalCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): IO[CommandApplication] =
    withAvailableSkill(state, playerId, SkillKind.Critical) { player =>
      spendSkillStamina(state, player, SkillKind.Critical) { chargedPlayer =>
        for
          updatedSkills <- updateSkill(chargedPlayer.skills, SkillKind.Critical, environment.rules.critical.runtime)
          updatedPlayer = chargedPlayer.copy(skills = updatedSkills)
          nextState <- replacePlayer(state, updatedPlayer)
          applied <- commandApplication(nextState, SkillKind.Critical, SkillOutcomeStatus.Applied, None)
        yield applied
      }
    }

  private def withAvailableSkill(
    state: BattleAggregateState,
    playerId: PlayerId,
    skillKind: SkillKind
  )(applyAvailable: BattlePlayerState => IO[CommandApplication]): IO[CommandApplication] =
    state.players.find(_.playerId == playerId) match {
      case None =>
        unavailableSkill(state, skillKind, SkillOutcomeReason.SkillNotOwned)
      case Some(player) =>
        availabilityFailure(player.skills, skillKind).flatMap {
          case Some(reason) => unavailableSkill(state, skillKind, reason)
          case None => applyAvailable(player)
        }
    }

  private def spendSkillStamina(
    state: BattleAggregateState,
    player: BattlePlayerState,
    skillKind: SkillKind
  )(applyCharged: BattlePlayerState => IO[CommandApplication]): IO[CommandApplication] =
    BattleSkillStaminaRules.spendIfAvailable(player, skillKind).flatMap {
      case BattleSkillStaminaRules.SpendResult.Spent(chargedPlayer) =>
        applyCharged(chargedPlayer)
      case BattleSkillStaminaRules.SpendResult.Insufficient(_, _) =>
        unavailableSkill(state, skillKind, SkillOutcomeReason.InsufficientStamina)
    }

  private def unavailableSkill(
    state: BattleAggregateState,
    skillKind: SkillKind,
    reason: SkillOutcomeReason
  ): IO[CommandApplication] =
    commandApplication(state, skillKind, SkillOutcomeStatus.Noop, Some(reason))

  private def commandApplication(
    state: BattleAggregateState,
    skillKind: SkillKind,
    status: SkillOutcomeStatus,
    reason: Option[SkillOutcomeReason]
  ): IO[CommandApplication] =
    skillOutcome(skillKind, status, reason).map(outcome => CommandApplication(state, Vector(outcome)))

  private def skillOutcome(
    skill: SkillKind,
    status: SkillOutcomeStatus,
    reason: Option[SkillOutcomeReason]
  ): IO[BattleCommandSkillOutcome] =
    IO.pure(BattleCommandSkillOutcome(skill, status, reason))

  private def updateSkill(
    skills: Vector[BattlePlayerSkillState],
    skillKind: SkillKind,
    runtime: BattleSkillRuntime
  ): IO[Vector[BattlePlayerSkillState]] =
    IO.pure(skills.map { skill =>
      if skill.skillKind == skillKind then
        skill.copy(cooldownMs = runtime.cooldownMs, activeMs = runtime.activeMs)
      else skill
    })

  private def isBlinkTargetAllowed(
    from: BattleVector2,
    target: BattleVector2,
    environment: BattleSkillCommandEnvironment
  ): IO[Boolean] =
    distanceBetween(from, target).map(_ <= environment.rules.blink.range.value)

  private def blinkDestination(target: BattleVector2): IO[BattleVector2] =
    IO.pure(target)

  private def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): IO[BattleAggregateState] =
    IO.pure(state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing)))

  private def normalizedDirection(next: BattleVector2): IO[BattleVector2] =
    vectorLength(next).map { length =>
      if length <= 0.0001 then BattleVector2(0.0, 0.0)
      else BattleVector2(next.x / length, next.y / length)
    }
}
