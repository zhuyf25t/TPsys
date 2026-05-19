package slaydemo.backend.battle.services.combat

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.world.BattleArenaCollision.*
import slaydemo.backend.battle.services.world.BattleGeometry.*
import slaydemo.backend.battle.services.world.BattleMotionRules.*

private[services] object BattleProjectileMotionRules {
  final case class ProjectileMotionResult(
    destination: BattleVector2,
    segmentEnd: BattleVector2,
    terminalReason: Option[ProjectileTerminalReason]
  )

  private final case class ProjectileBlock(
    t: Double,
    reason: ProjectileTerminalReason
  )

  /** 中文名：解析投射物运动（resolveProjectileMotion）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def resolveProjectileMotion(
    projectile: BattleProjectileState,
    speedFactor: Double,
    deltaMs: Long
  ): ProjectileMotionResult = {
    val direction = normalizeMovement(projectile.velocity)
    val distance = vectorLength(projectile.velocity) * speedFactor * math.max(0L, deltaMs).toDouble / 1000.0
    val end = add(projectile.position, scale(direction, distance))
    val block = firstProjectileBlock(projectile.position, end, projectile.radius.value)

    block match {
      case Some(value) =>
        ProjectileMotionResult(
          destination = pointAtSegmentT(projectile.position, end, value.t),
          segmentEnd = end,
          terminalReason = Some(value.reason)
        )
      case None =>
        ProjectileMotionResult(destination = end, segmentEnd = end, terminalReason = None)
    }
  }

  private def firstProjectileBlock(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double
  ): Option[ProjectileBlock] = {
    val worldExit = firstSegmentWorldExitT(start, end, radius).map(t => ProjectileBlock(t, ProjectileTerminalReason.OutOfBounds))
    val obstacleEnter = BattleArenaCatalog.ArenaObstacles
      .flatMap(obstacle => firstSegmentObstacleEnterT(start, end, radius, obstacle))
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
