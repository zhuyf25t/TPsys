package services.battle.microservices.combat.services

import cats.effect.IO

import services.battle.microservices.combat.objects.projectile.ProjectileTerminalReason
import services.battle.objects.core.BattleVector2
import services.battle.microservices.combat.objects.projectile.BattleProjectileState
import services.battle.microservices.world.services.BattleGeometry.*

private[battle] object BattleProjectileMotionRules {
  final case class ProjectileMotionResult(
    destination: BattleVector2,
    segmentEnd: BattleVector2,
    terminalReason: Option[ProjectileTerminalReason]
  )

  final case class ProjectileBlock(
    t: Double,
    reason: ProjectileTerminalReason
  )

  def resolveProjectileMotion(
    projectile: BattleProjectileState,
    speedFactor: Double,
    deltaMs: Long,
    normalizeMovement: BattleVector2 => IO[BattleVector2],
    firstProjectileBlock: (BattleVector2, BattleVector2, Double) => IO[Option[ProjectileBlock]]
  ): IO[ProjectileMotionResult] =
    for
      direction <- normalizeMovement(projectile.velocity)
      velocityLength <- vectorLength(projectile.velocity)
      distance <- IO.pure(velocityLength * speedFactor * math.max(0L, deltaMs).toDouble / 1000.0)
      offset <- scale(direction, distance)
      end <- add(projectile.position, offset)
      block <- firstProjectileBlock(projectile.position, end, projectile.radius.value)
      result <- block match {
        case Some(value) =>
          pointAtSegmentT(projectile.position, end, value.t).map { destination =>
            ProjectileMotionResult(
              destination = destination,
              segmentEnd = end,
              terminalReason = Some(value.reason)
            )
          }
        case None =>
          IO.pure(ProjectileMotionResult(destination = end, segmentEnd = end, terminalReason = None))
      }
    yield result
}
