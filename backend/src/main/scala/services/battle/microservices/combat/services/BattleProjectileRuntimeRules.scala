package services.battle.microservices.combat.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.combat.services.BattleProjectileImpactRules.*
import services.battle.microservices.world.services.BattleArenaCollision
import services.battle.microservices.world.services.BattleMotionRules
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.combat.services.BattleProjectileMotionRules.*
import services.battle.microservices.combat.services.BattleProjectileTargetingRules.*
import services.battle.microservices.runtime.services.BattleTimeRules.*
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.combat.objects.projectile.ProjectileTerminalReason
import services.battle.objects.core.{BattleAggregateState, DurationMillis, Radius}
import services.battle.microservices.combat.objects.projectile.BattleProjectileState

private[battle] object BattleProjectileRuntimeRules {
  private final case class ProjectileAdvance(
    state: BattleAggregateState,
    activeProjectiles: Vector[BattleProjectileState]
  )

  def advanceProjectiles(
    state: BattleAggregateState,
    deltaMs: Long,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    for
      historyRules <- battleRules.history
      movementRules <- battleRules.movement
      advanced <- state.projectiles.foldLeft(IO.pure(ProjectileAdvance(state, Vector.empty))) { (currentIO, projectile) =>
        currentIO.flatMap { current =>
          val travelMs = math.min(math.max(0L, deltaMs), math.max(0L, projectile.ttlMs.value))
          for
            _ <- IO.cede
            slowed <- state.slowFields.existsM(field => distanceBetween(projectile.position, field.position).map(_ <= field.radius.value))
            speedFactor = if slowed then movementRules.slowFieldProjectileFactor.value else 1.0
            motion <- resolveProjectileMotion(
              projectile = projectile,
              speedFactor = speedFactor,
              deltaMs = travelMs,
              normalizeMovement = BattleMotionRules.normalizeMovement,
              firstProjectileBlock = (start, end, radius) => firstProjectileBlock(start, end, radius, arena)
            )
            nextTtl <- decrementLong(projectile.ttlMs.value, travelMs)
            playerHit <- findProjectilePlayerHit(
              players = current.state.players,
              projectile = projectile,
              destination = motion.destination,
              hitRadius = Radius(
                projectile.radius.value +
                  arena.playerCollisionRadius +
                  arena.projectileShooterAdvantageRadius
              ),
              segmentCircleHitT = (start, end, center, radius) =>
                BattleArenaCollision.segmentCircleHitT(start, end, center, radius.value)
            )
            reason = playerHit match {
              case Some(_) => Some(ProjectileTerminalReason.Hit)
              case None =>
                if nextTtl <= 0L then Some(ProjectileTerminalReason.Expired)
                else motion.terminalReason
            }
            nextAdvance <- (reason, playerHit) match {
              case (Some(terminalReason), Some(hit)) =>
                applyProjectileImpact(
                  current.state,
                  projectile,
                  terminalReason,
                  hit.position,
                  motion.segmentEnd,
                  nextTtl,
                  Some(hit.player),
                  arena.playerCollisionRadius,
                  historyRules.retainedProjectileTerminalCount,
                  historyRules.retainedBattleEventCount
                ).map(nextState => current.copy(state = nextState))
              case (Some(terminalReason), None) =>
                applyProjectileImpact(
                  current.state,
                  projectile,
                  terminalReason,
                  motion.destination,
                  motion.segmentEnd,
                  nextTtl,
                  None,
                  arena.playerCollisionRadius,
                  historyRules.retainedProjectileTerminalCount,
                  historyRules.retainedBattleEventCount
                ).map(nextState => current.copy(state = nextState))
              case (None, _) =>
                IO.pure(current.copy(
                  activeProjectiles = current.activeProjectiles :+ projectile.copy(
                    position = motion.destination,
                    ttlMs = DurationMillis(nextTtl)
                  )
                ))
            }
          yield nextAdvance
        }
      }
    yield advanced.state.copy(projectiles = advanced.activeProjectiles)

  private def firstProjectileBlock(
    start: services.battle.objects.core.BattleVector2,
    end: services.battle.objects.core.BattleVector2,
    radius: Double,
    arena: BattleArenaContext
  ): IO[Option[ProjectileBlock]] =
    for
      worldExit <- BattleArenaCollision
        .firstSegmentWorldExitT(start, end, radius, arena)
        .map(_.map(t => ProjectileBlock(t, ProjectileTerminalReason.OutOfBounds)))
      obstacleEnter <- arena.arenaObstacles
        .traverse(obstacle => BattleArenaCollision.firstSegmentObstacleEnterT(start, end, radius, obstacle))
        .map(_.flatten.minOption.map(t => ProjectileBlock(t, ProjectileTerminalReason.Blocked)))
      block <- IO.pure {
        (worldExit, obstacleEnter) match {
          case (Some(world), Some(obstacle)) if world.t <= obstacle.t => Some(world)
          case (Some(_), Some(obstacle))                             => Some(obstacle)
          case (Some(world), None)                                   => Some(world)
          case (None, Some(obstacle))                                => Some(obstacle)
          case (None, None)                                          => None
        }
      }
    yield block
}
