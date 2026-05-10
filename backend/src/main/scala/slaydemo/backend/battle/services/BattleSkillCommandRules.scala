package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.{BattleCommandRequest, BattleCommandSkillOutcome}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleAggregateUpdateRules.*
import slaydemo.backend.battle.services.BattleArenaCollision.*
import slaydemo.backend.battle.services.BattleGeometry.*
import slaydemo.backend.battle.services.BattleMotionRules.*
import slaydemo.backend.battle.services.BattleSkillRules.*

private[services] object BattleSkillCommandRules {
  final case class CommandApplication(
    state: BattleAggregateState,
    outcomes: Vector[BattleCommandSkillOutcome]
  )

  def applyBlinkCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest
  ): CommandApplication =
    withAvailableSkill(state, playerId, SkillKind.Blink) { player =>
      request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
        case None =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))))
        case Some(target) if !isInWorld(target, BattleArenaCatalog.PlayerCollisionRadius) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))))
        case Some(target) if collidesWithArenaObstacles(target, BattleArenaCatalog.PlayerCollisionRadius) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
        case Some(target) if !isBlinkTargetAllowed(player.position, target) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))))
        case Some(target) =>
          val destination = blinkDestination(target)
          if isBlockedPoint(destination) then
            CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
          else {
            val movedPlayer = player
              .copy(
                position = destination,
                skills = updateSkill(player.skills, SkillKind.Blink, BattleSkillCatalog.Blink.runtime)
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
    request: BattleCommandRequest
  ): CommandApplication =
    withAvailableSkill(state, playerId, SkillKind.Dash) { player =>
      val direction = normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
      val dashDirection =
        if vectorLength(direction) > 0.0 then direction else player.aim
      if vectorLength(dashDirection) <= 0.0 then
        CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.NoDirection))))
      else {
        val motion = findMotionDestination(
          position = player.position,
          direction = dashDirection,
          distance = BattleSkillCatalog.Dash.distance.value,
          radius = BattleArenaCatalog.PlayerCollisionRadius
        )
        if distanceBetween(player.position, motion.destination) <= 0.001 then
          CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
        else {
          val movedPlayer = player.copy(
            position = motion.destination,
            skills = updateSkill(player.skills, SkillKind.Dash, BattleSkillCatalog.Dash.runtime)
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
    request: BattleCommandRequest
  ): CommandApplication =
    withAvailableSkill(state, playerId, SkillKind.Freeze) { player =>
      request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
        case None =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))))
        case Some(target) if !isInWorld(target) =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))))
        case Some(target) if distanceBetween(player.position, target) > BattleSkillCatalog.Freeze.castRange.value =>
          CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))))
        case Some(target) =>
          val field = BattleSlowFieldState(
            fieldId = SlowFieldId(s"slow-${player.playerId.value}-${request.clientCommandSeq.value}"),
            ownerPlayerId = player.playerId,
            ownerHeroId = player.heroId,
            position = target,
            radius = BattleSkillCatalog.Freeze.radius,
            ttlMs = BattleSkillCatalog.Freeze.runtime.activeMs,
            durationMs = BattleSkillCatalog.Freeze.runtime.activeMs
          )
          val updatedPlayer = player.copy(
            skills = updateSkill(player.skills, SkillKind.Freeze, BattleSkillCatalog.Freeze.runtime)
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
    runtime: BattleSkillCatalog.SkillRuntime
  ): Vector[BattlePlayerSkillState] =
    skills.map { skill =>
      if skill.skillKind == skillKind then
        skill.copy(cooldownMs = runtime.cooldownMs, activeMs = runtime.activeMs)
      else skill
    }

  private def isBlinkTargetAllowed(from: BattleVector2, target: BattleVector2): Boolean = {
    val distance = distanceBetween(from, target)
    distance <= BattleSkillCatalog.Blink.range.value
  }

  private def blinkDestination(target: BattleVector2): BattleVector2 =
    target
}
