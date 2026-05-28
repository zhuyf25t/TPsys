package services.battle.objects.combat

import services.battle.objects.ProjectileTerminalReason
import services.battle.objects.core.BattleVector2
import services.battle.objects.projectile.BattleProjectileState
import services.battle.objects.world.BattleGeometry.*

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
    normalizeMovement: BattleVector2 => BattleVector2,
    firstProjectileBlock: (BattleVector2, BattleVector2, Double) => Option[ProjectileBlock]
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
}