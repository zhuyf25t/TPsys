package services.battle.microservices.combat.services

import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.combat.services.BattleProjectileImpactRules.*
import services.battle.microservices.world.services.{BattleArenaCatalog, BattleArenaCollision}
import services.battle.microservices.world.services.BattleMotionRules
import services.battle.microservices.combat.services.BattleProjectileMotionRules.*
import services.battle.microservices.combat.services.BattleProjectileTargetingRules.*
import services.battle.microservices.runtime.services.BattleTimeRules.*
import services.battle.microservices.runtime.database.BattleRuntimeRuleBook
import services.battle.microservices.world.database.BattleWorldRuleBook
import services.battle.microservices.combat.objects.projectile.ProjectileTerminalReason
import services.battle.objects.core.{BattleAggregateState, DurationMillis, Radius}
import services.battle.microservices.combat.objects.projectile.BattleProjectileState

private[battle] object BattleProjectileRuntimeRules {
  private final case class ProjectileAdvance(
    state: BattleAggregateState,
    activeProjectiles: Vector[BattleProjectileState]
  )

  /** 中文名：推进projectiles（advanceProjectiles）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def advanceProjectiles(state: BattleAggregateState, deltaMs: Long): BattleAggregateState = {
    val advanced = state.projectiles.foldLeft(ProjectileAdvance(state, Vector.empty)) { (current, projectile) =>
      val travelMs = math.min(math.max(0L, deltaMs), math.max(0L, projectile.ttlMs.value))
      val speedFactor =
        if state.slowFields.exists(field => distanceBetween(projectile.position, field.position) <= field.radius.value) then
          BattleWorldRuleBook.movement.slowFieldProjectileFactor.value
        else 1.0
      val motion =
        resolveProjectileMotion(
          projectile = projectile,
          speedFactor = speedFactor,
          deltaMs = travelMs,
          normalizeMovement = BattleMotionRules.normalizeMovement,
          firstProjectileBlock = firstProjectileBlock
        )
      val nextTtl = decrementLong(projectile.ttlMs.value, travelMs)
      val playerHit =
        findProjectilePlayerHit(
          players = current.state.players,
          projectile = projectile,
          destination = motion.destination,
          hitRadius = Radius(
            projectile.radius.value +
              BattleArenaCatalog.PlayerCollisionRadius +
              BattleArenaCatalog.ProjectileShooterAdvantageRadius
          ),
          segmentCircleHitT = (start, end, center, radius) =>
            BattleArenaCollision.segmentCircleHitT(start, end, center, radius.value)
        )
      val reason = playerHit match {
        case Some(_) => Some(ProjectileTerminalReason.Hit)
        case None =>
          if nextTtl <= 0L then Some(ProjectileTerminalReason.Expired)
          else motion.terminalReason
      }

      (reason, playerHit) match {
        case (Some(terminalReason), Some(hit)) =>
          current.copy(
            state = applyProjectileImpact(
              current.state,
              projectile,
              terminalReason,
              hit.position,
              motion.segmentEnd,
              nextTtl,
              Some(hit.player),
              BattleRuntimeRuleBook.history.retainedProjectileTerminalCount,
              BattleRuntimeRuleBook.history.retainedBattleEventCount
            )
          )
        case (Some(terminalReason), None) =>
          current.copy(
            state = applyProjectileImpact(
              current.state,
              projectile,
              terminalReason,
              motion.destination,
              motion.segmentEnd,
              nextTtl,
              None,
              BattleRuntimeRuleBook.history.retainedProjectileTerminalCount,
              BattleRuntimeRuleBook.history.retainedBattleEventCount
            )
          )
        case (None, _) =>
          current.copy(
            activeProjectiles = current.activeProjectiles :+ projectile.copy(
              position = motion.destination,
              ttlMs = DurationMillis(nextTtl)
            )
          )
      }
    }

    advanced.state.copy(projectiles = advanced.activeProjectiles)
  }

  private def firstProjectileBlock(
    start: services.battle.objects.core.BattleVector2,
    end: services.battle.objects.core.BattleVector2,
    radius: Double
  ): Option[ProjectileBlock] = {
    val worldExit =
      BattleArenaCollision
        .firstSegmentWorldExitT(start, end, radius)
        .map(t => ProjectileBlock(t, ProjectileTerminalReason.OutOfBounds))
    val obstacleEnter =
      BattleArenaCatalog.ArenaObstacles
        .flatMap(obstacle => BattleArenaCollision.firstSegmentObstacleEnterT(start, end, radius, obstacle))
        .minOption
        .map(t => ProjectileBlock(t, ProjectileTerminalReason.Blocked))

    (worldExit, obstacleEnter) match {
      case (Some(world), Some(obstacle)) if world.t <= obstacle.t => Some(world)
      case (Some(_), Some(obstacle))                             => Some(obstacle)
      case (Some(world), None)                                   => Some(world)
      case (None, Some(obstacle))                                => Some(obstacle)
      case (None, None)                                          => None
    }
  }
}
