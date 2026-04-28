package slaydemo.backend.battle.runtime

import scala.annotation.tailrec

import slaydemo.backend.battle.objects.BattleVector2

final case class AuthoritativeArenaObstacle(
  obstacleId: String,
  kind: String,
  position: BattleVector2,
  size: BattleVector2
)

final case class MotionDestination(
  destination: BattleVector2,
  blocked: Boolean
)

object AuthoritativeArenaGeometry {
  private final case class SteppedMotionResult(
    destination: BattleVector2,
    blocked: Boolean,
    hitBlocker: Boolean
  )

  val WorldSize: BattleVector2 = BattleMapCatalog.defaultMap.worldSize
  val HeroRadius: Double = 18.0
  val ProjectileRadius: Double = 8.0
  private val FloorTileSize: Int = 64
  private val BorderObstacleSize: BattleVector2 = BattleVector2(FloorTileSize.toDouble, FloorTileSize.toDouble)

  private val BorderObstacles: Vector[AuthoritativeArenaObstacle] = {
    val horizontal = (FloorTileSize / 2 until WorldSize.x.toInt by FloorTileSize).toVector.flatMap { x =>
      Vector(
        AuthoritativeArenaObstacle(s"border-top-$x", "wall", BattleVector2(x.toDouble, FloorTileSize / 2.0), BorderObstacleSize),
        AuthoritativeArenaObstacle(s"border-bottom-$x", "wall", BattleVector2(x.toDouble, WorldSize.y - FloorTileSize / 2.0), BorderObstacleSize)
      )
    }
    val vertical = (FloorTileSize + FloorTileSize / 2 until WorldSize.y.toInt - FloorTileSize / 2 by FloorTileSize).toVector.flatMap { y =>
      Vector(
        AuthoritativeArenaObstacle(s"border-left-$y", "wall", BattleVector2(FloorTileSize / 2.0, y.toDouble), BorderObstacleSize),
        AuthoritativeArenaObstacle(s"border-right-$y", "wall", BattleVector2(WorldSize.x - FloorTileSize / 2.0, y.toDouble), BorderObstacleSize)
      )
    }

    horizontal ++ vertical
  }

  private val InnerObstacles: Vector[AuthoritativeArenaObstacle] =
    BattleMapCatalog.defaultMap.innerObstacles.map(toAuthoritativeObstacle)

  private def toAuthoritativeObstacle(definition: BattleMapCatalog.ArenaObstacleDefinition): AuthoritativeArenaObstacle =
    AuthoritativeArenaObstacle(
      obstacleId = definition.obstacleId,
      kind = definition.kind,
      position = definition.position,
      size = definition.size
    )

  val Obstacles: Vector[AuthoritativeArenaObstacle] = BorderObstacles ++ InnerObstacles

  def findMotionDestination(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double
  ): MotionDestination = {
    val normalized = normalize(direction)
    val clampedDistance = math.max(0.0, distance)
    val fullMotion = resolveSteppedMotion(position, normalized, clampedDistance, radius)

    if (!fullMotion.hitBlocker) {
      toMotionDestination(fullMotion)
    } else {
      val xDistance = math.abs(normalized.x * clampedDistance)
      val yDistance = math.abs(normalized.y * clampedDistance)
      val xMotion =
        if (xDistance > 0.0) {
          resolveSteppedMotion(position, BattleVector2(math.signum(normalized.x), 0.0), xDistance, radius)
        } else {
          fullMotion
        }
      val yMotion =
        if (yDistance > 0.0) {
          resolveSteppedMotion(position, BattleVector2(0.0, math.signum(normalized.y)), yDistance, radius)
        } else {
          fullMotion
        }

      toMotionDestination(resolveBestMotion(position, Vector(fullMotion, xMotion, yMotion)))
    }
  }

  private def resolveSteppedMotion(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double
  ): SteppedMotionResult = {
    val steps = math.ceil(math.max(0.0, distance) / 16.0).toInt
    val stepped = stepMotion(position, position, direction, distance, radius, steps, 1)

    SteppedMotionResult(
      destination = stepped.destination,
      blocked = stepped.destination.x == position.x && stepped.destination.y == position.y,
      hitBlocker = stepped.hitBlocker
    )
  }

  @tailrec
  private def stepMotion(
    origin: BattleVector2,
    lastValid: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double,
    steps: Int,
    step: Int
  ): SteppedMotionResult =
    if (step > steps) {
      SteppedMotionResult(lastValid, blocked = false, hitBlocker = false)
    } else {
      val travel = math.min(distance, step.toDouble * 16.0)
      val candidate = BattleVector2(
        x = origin.x + direction.x * travel,
        y = origin.y + direction.y * travel
      )

      if (!canOccupy(candidate, radius)) {
        SteppedMotionResult(lastValid, blocked = false, hitBlocker = true)
      } else {
        stepMotion(origin, candidate, direction, distance, radius, steps, step + 1)
      }
    }

  private def toMotionDestination(result: SteppedMotionResult): MotionDestination =
    MotionDestination(destination = result.destination, blocked = result.blocked)

  private def motionProgress(destination: BattleVector2, origin: BattleVector2): Double =
    math.hypot(destination.x - origin.x, destination.y - origin.y)

  private def resolveBestMotion(origin: BattleVector2, motions: Vector[SteppedMotionResult]): SteppedMotionResult =
    motions.maxBy(motion => motionProgress(motion.destination, origin))

  def canOccupy(position: BattleVector2, radius: Double): Boolean =
    isInsideWorld(position, radius) && !collidesWithObstacles(position, radius)

  def isCenterInsideWorld(position: BattleVector2): Boolean =
    position.x >= 0.0 && position.x <= WorldSize.x && position.y >= 0.0 && position.y <= WorldSize.y

  def isInsideWorld(position: BattleVector2, radius: Double): Boolean =
    position.x >= radius &&
      position.x <= WorldSize.x - radius &&
      position.y >= radius &&
      position.y <= WorldSize.y - radius

  def collidesWithObstacles(position: BattleVector2, radius: Double): Boolean =
    Obstacles.exists(intersectsObstacle(position, radius, _))

  def intersectsObstacle(position: BattleVector2, radius: Double, obstacle: AuthoritativeArenaObstacle): Boolean = {
    val dx = math.abs(position.x - obstacle.position.x)
    val dy = math.abs(position.y - obstacle.position.y)
    dx < radius + obstacle.size.x / 2.0 && dy < radius + obstacle.size.y / 2.0
  }

  private def normalize(vector: BattleVector2): BattleVector2 = {
    val length = math.hypot(vector.x, vector.y)
    if (length <= 0.0001) BattleVector2(0.0, 0.0)
    else BattleVector2(vector.x / length, vector.y / length)
  }
}
