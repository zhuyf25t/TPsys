package services.battle.database.world

import services.battle.database.world.BattleArenaCollision.*
import services.battle.database.world.BattleGeometry.*
import services.battle.objects.BattleVector2

private[services] object BattleMotionRules {
  final case class SteppedMotionResult(
    destination: BattleVector2,
    blocked: Boolean,
    hitBlocker: Boolean
  )

  private final case class SteppedMotionScan(
    lastValid: BattleVector2,
    hitBlocker: Boolean
  )

  def normalizeMovement(next: BattleVector2): BattleVector2 = {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then BattleArenaCatalog.ZeroVector
    else BattleVector2(next.x / length, next.y / length)
  }

  def findMotionDestination(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double
  ): SteppedMotionResult = {
    val normalized = normalizeMovement(direction)
    val clampedDistance = math.max(0.0, distance)
    val fullMotion = resolveSteppedMotion(position, normalized, clampedDistance, radius)

    if !fullMotion.hitBlocker then fullMotion
    else {
      val xDistance = math.abs(normalized.x * clampedDistance)
      val yDistance = math.abs(normalized.y * clampedDistance)
      val xMotion =
        if xDistance > 0.0 then resolveSteppedMotion(position, BattleVector2(math.signum(normalized.x), 0.0), xDistance, radius)
        else fullMotion
      val yMotion =
        if yDistance > 0.0 then resolveSteppedMotion(position, BattleVector2(0.0, math.signum(normalized.y)), yDistance, radius)
        else fullMotion

      Vector(fullMotion, xMotion, yMotion).maxBy(motion => distanceBetween(position, motion.destination))
    }
  }

  private def resolveSteppedMotion(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double
  ): SteppedMotionResult = {
    val clampedDistance = math.max(0.0, distance)
    val steps = math.ceil(clampedDistance / BattleArenaCatalog.MotionStepSize).toInt
    val scan = (1 to steps).foldLeft(SteppedMotionScan(position, hitBlocker = false)) { (current, step) =>
      if current.hitBlocker then current
      else {
        val travel = math.min(clampedDistance, step.toDouble * BattleArenaCatalog.MotionStepSize)
        val candidate = add(position, scale(direction, travel))
        if canPlayerOccupy(candidate, radius) then current.copy(lastValid = candidate)
        else current.copy(hitBlocker = true)
      }
    }

    SteppedMotionResult(
      destination = scan.lastValid,
      blocked = scan.lastValid == position,
      hitBlocker = scan.hitBlocker
    )
  }
}
