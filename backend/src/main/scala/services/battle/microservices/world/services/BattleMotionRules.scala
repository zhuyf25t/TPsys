package services.battle.microservices.world.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.world.services.BattleArenaCollision.*
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.objects.BattleVector2

private[battle] object BattleMotionRules {
  final case class SteppedMotionResult(
    destination: BattleVector2,
    blocked: Boolean,
    hitBlocker: Boolean
  )

  private final case class SteppedMotionScan(
    lastValid: BattleVector2,
    hitBlocker: Boolean
  )

  def normalizeMovement(next: BattleVector2): IO[BattleVector2] = IO.pure {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then BattleArenaContext.ZeroVector
    else BattleVector2(next.x / length, next.y / length)
  }

  def findMotionDestination(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double,
    arena: BattleArenaContext
  ): IO[SteppedMotionResult] =
    for
      normalized <- normalizeMovement(direction)
      clampedDistance <- IO.pure(math.max(0.0, distance))
      fullMotion <- resolveSteppedMotion(position, normalized, clampedDistance, radius, arena)
      result <-
        if !fullMotion.hitBlocker then IO.pure(fullMotion)
        else {
          val xDistance = math.abs(normalized.x * clampedDistance)
          val yDistance = math.abs(normalized.y * clampedDistance)
          val xMotionIO =
            if xDistance > 0.0 then resolveSteppedMotion(position, BattleVector2(math.signum(normalized.x), 0.0), xDistance, radius, arena)
            else IO.pure(fullMotion)
          val yMotionIO =
            if yDistance > 0.0 then resolveSteppedMotion(position, BattleVector2(0.0, math.signum(normalized.y)), yDistance, radius, arena)
            else IO.pure(fullMotion)

          for
            xMotion <- xMotionIO
            yMotion <- yMotionIO
            scored <- Vector(fullMotion, xMotion, yMotion).traverse { motion =>
              distanceBetween(position, motion.destination).map(distance => motion -> distance)
            }
          yield scored.maxBy { case (_, distance) => distance }._1
        }
    yield result

  private def resolveSteppedMotion(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double,
    arena: BattleArenaContext
  ): IO[SteppedMotionResult] = {
    val clampedDistance = math.max(0.0, distance)
    val steps = math.ceil(clampedDistance / arena.motionStepSize).toInt
    val scanIO = (1 to steps).foldLeft(IO.pure(SteppedMotionScan(position, hitBlocker = false))) { (currentIO, step) =>
      currentIO.flatMap { current =>
        if current.hitBlocker then IO.pure(current)
        else {
          val travel = math.min(clampedDistance, step.toDouble * arena.motionStepSize)
          for
            offset <- scale(direction, travel)
            candidate <- add(position, offset)
            canOccupy <- canPlayerOccupy(candidate, radius, arena)
          yield canOccupy match {
            case true  => current.copy(lastValid = candidate)
            case false => current.copy(hitBlocker = true)
          }
        }
      }
    }

    scanIO.map { scan =>
      SteppedMotionResult(
        destination = scan.lastValid,
        blocked = scan.lastValid == position,
        hitBlocker = scan.hitBlocker
      )
    }
  }
}
