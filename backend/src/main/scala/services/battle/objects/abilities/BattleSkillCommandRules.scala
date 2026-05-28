package services.battle.objects.abilities

import services.battle.objects.world.BattleGeometry.*
import services.battle.objects.abilities.BattleSkillRules.availabilityFailure
import services.battle.objects.{SkillKind, SkillOutcomeReason, SkillOutcomeStatus}
import services.battle.objects.command.{BattleCommandRequest, BattleCommandSkillOutcome}
import services.battle.objects.core.{BattleAggregateState, BattleVector2, PlayerId, Radius, SlowFieldId}
import services.battle.objects.player.{BattlePlayerSkillState, BattlePlayerState}
import services.battle.objects.skill.BattleSlowFieldState

private[battle] object BattleSkillCommandRules {
  final case class CommandApplication(
    state: BattleAggregateState,
    outcomes: Vector[BattleCommandSkillOutcome]
  )

  final case class BattleSkillCommandEnvironment(
    rules: BattleSkillRuleSet,
    playerCollisionRadius: Radius,
    isInWorld: BattleVector2 => Boolean,
    isInWorldWithRadius: (BattleVector2, Radius) => Boolean,
    collidesWithArenaObstacles: (BattleVector2, Radius) => Boolean,
    isBlockedPoint: BattleVector2 => Boolean,
    motionDestination: (BattleVector2, BattleVector2, SkillDistance, Radius) => BattleVector2
  )

  def applyBlinkCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): CommandApplication =
    withAvailableSkill(state, playerId, SkillKind.Blink) { player =>
      request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
        case None =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))))
        case Some(target) if !environment.isInWorldWithRadius(target, environment.playerCollisionRadius) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))))
        case Some(target) if environment.collidesWithArenaObstacles(target, environment.playerCollisionRadius) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
        case Some(target) if !isBlinkTargetAllowed(player.position, target, environment) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))))
        case Some(target) =>
          val destination = blinkDestination(target)
          if environment.isBlockedPoint(destination) then
            CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
          else {
            val movedPlayer = player
              .copy(
                position = destination,
                skills = updateSkill(player.skills, SkillKind.Blink, environment.rules.blink.runtime)
              )
            CommandApplication(
              replacePlayer(state, movedPlayer),
              Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Applied, None))
            )
          }
      }
    }

  def applyDashCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): CommandApplication =
    withAvailableSkill(state, playerId, SkillKind.Dash) { player =>
      val direction = normalizedDirection(BattleVector2(request.movement.x, request.movement.y))
      val dashDirection =
        if vectorLength(direction) > 0.0 then direction else player.aim
      if vectorLength(dashDirection) <= 0.0 then
        CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.NoDirection))))
      else {
        val destination =
          environment.motionDestination(
            player.position,
            dashDirection,
            environment.rules.dash.distance,
            environment.playerCollisionRadius
          )
        if distanceBetween(player.position, destination) <= 0.001 then
          CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
        else {
          val movedPlayer = player.copy(
            position = destination,
            skills = updateSkill(player.skills, SkillKind.Dash, environment.rules.dash.runtime)
          )
          CommandApplication(
            replacePlayer(state, movedPlayer),
            Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Applied, None))
          )
        }
      }
    }

  def applyFreezeCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest,
    environment: BattleSkillCommandEnvironment
  ): CommandApplication =
    withAvailableSkill(state, playerId, SkillKind.Freeze) { player =>
      request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
        case None =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))))
        case Some(target) if !environment.isInWorld(target) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))))
        case Some(target) if distanceBetween(player.position, target) > environment.rules.freeze.castRange.value =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))))
        case Some(target) =>
          val field = BattleSlowFieldState(
            fieldId = SlowFieldId(s"slow-${player.playerId.value}-${request.clientCommandSeq.value}"),
            ownerPlayerId = player.playerId,
            ownerHeroId = player.heroId,
            position = target,
            radius = environment.rules.freeze.radius,
            ttlMs = environment.rules.freeze.runtime.activeMs,
            durationMs = environment.rules.freeze.runtime.activeMs
          )
          val updatedPlayer = player.copy(
            skills = updateSkill(player.skills, SkillKind.Freeze, environment.rules.freeze.runtime)
          )
          CommandApplication(
            replacePlayer(state, updatedPlayer).copy(slowFields = state.slowFields :+ field),
            Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Applied, None))
          )
      }
    }

  private def withAvailableSkill(
    state: BattleAggregateState,
    playerId: PlayerId,
    skillKind: SkillKind
  )(applyAvailable: BattlePlayerState => CommandApplication): CommandApplication =
    state.players.find(_.playerId == playerId) match {
      case None =>
        unavailableSkill(state, skillKind, SkillOutcomeReason.SkillNotOwned)
      case Some(player) =>
        availabilityFailure(player.skills, skillKind) match {
          case Some(reason) => unavailableSkill(state, skillKind, reason)
          case None         => applyAvailable(player)
        }
    }

  private def unavailableSkill(
    state: BattleAggregateState,
    skillKind: SkillKind,
    reason: SkillOutcomeReason
  ): CommandApplication =
    CommandApplication(state, Vector(skillOutcome(skillKind, SkillOutcomeStatus.Noop, Some(reason))))

  private def skillOutcome(
    skill: SkillKind,
    status: SkillOutcomeStatus,
    reason: Option[SkillOutcomeReason]
  ): BattleCommandSkillOutcome =
    BattleCommandSkillOutcome(skill, status, reason)

  private def updateSkill(
    skills: Vector[BattlePlayerSkillState],
    skillKind: SkillKind,
    runtime: BattleSkillRuntime
  ): Vector[BattlePlayerSkillState] =
    skills.map { skill =>
      if skill.skillKind == skillKind then
        skill.copy(cooldownMs = runtime.cooldownMs, activeMs = runtime.activeMs)
      else skill
    }

  private def isBlinkTargetAllowed(
    from: BattleVector2,
    target: BattleVector2,
    environment: BattleSkillCommandEnvironment
  ): Boolean =
    distanceBetween(from, target) <= environment.rules.blink.range.value

  private def blinkDestination(target: BattleVector2): BattleVector2 =
    target

  private def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): BattleAggregateState =
    state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing))

  private def normalizedDirection(next: BattleVector2): BattleVector2 = {
    val length = vectorLength(next)
    if length <= 0.0001 then BattleVector2(0.0, 0.0)
    else BattleVector2(next.x / length, next.y / length)
  }
}
