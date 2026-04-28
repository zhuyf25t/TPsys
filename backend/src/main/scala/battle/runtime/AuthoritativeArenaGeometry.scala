package slaydemo.backend.battle.runtime

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

  val WorldSize: BattleVector2 = BattleVector2(2560.0, 1600.0)
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
    Vector(
      AuthoritativeArenaObstacle("cover-nw-1", "wall", BattleVector2(416.0, 416.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-nw-2", "wall", BattleVector2(480.0, 416.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-nw-3", "wall", BattleVector2(416.0, 480.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-ne-1", "wall", BattleVector2(2144.0, 416.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-ne-2", "wall", BattleVector2(2080.0, 416.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-ne-3", "wall", BattleVector2(2144.0, 480.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-sw-1", "wall", BattleVector2(416.0, 1184.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-sw-2", "wall", BattleVector2(480.0, 1184.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-sw-3", "wall", BattleVector2(416.0, 1120.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-se-1", "wall", BattleVector2(2144.0, 1184.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-se-2", "wall", BattleVector2(2080.0, 1184.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("cover-se-3", "wall", BattleVector2(2144.0, 1120.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-top-1", "wall", BattleVector2(1184.0, 448.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-top-2", "wall", BattleVector2(1248.0, 448.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-top-3", "wall", BattleVector2(1312.0, 448.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-top-4", "wall", BattleVector2(1376.0, 448.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-bot-1", "wall", BattleVector2(1184.0, 1152.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-bot-2", "wall", BattleVector2(1248.0, 1152.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-bot-3", "wall", BattleVector2(1312.0, 1152.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("center-bot-4", "wall", BattleVector2(1376.0, 1152.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-left-1", "wall", BattleVector2(928.0, 640.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-left-2", "wall", BattleVector2(928.0, 704.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-left-3", "wall", BattleVector2(928.0, 896.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-left-4", "wall", BattleVector2(928.0, 960.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-right-1", "wall", BattleVector2(1632.0, 640.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-right-2", "wall", BattleVector2(1632.0, 704.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-right-3", "wall", BattleVector2(1632.0, 896.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-right-4", "wall", BattleVector2(1632.0, 960.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("crate-mid-top-left", "crate", BattleVector2(1184.0, 736.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("crate-mid-top-right", "crate", BattleVector2(1376.0, 736.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("crate-mid-bottom-left", "crate", BattleVector2(1184.0, 864.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("crate-mid-bottom-right", "crate", BattleVector2(1376.0, 864.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("mid-west-1", "wall", BattleVector2(640.0, 704.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("mid-west-2", "wall", BattleVector2(640.0, 896.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("mid-east-1", "wall", BattleVector2(1920.0, 704.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("mid-east-2", "wall", BattleVector2(1920.0, 896.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-top-left", "wall", BattleVector2(1056.0, 608.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-top-right", "wall", BattleVector2(1504.0, 608.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-bottom-left", "wall", BattleVector2(1056.0, 992.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("lane-bottom-right", "wall", BattleVector2(1504.0, 992.0), BattleVector2(64.0, 64.0)),
      AuthoritativeArenaObstacle("crate-west-top", "crate", BattleVector2(768.0, 640.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("crate-west-bottom", "crate", BattleVector2(768.0, 960.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("crate-east-top", "crate", BattleVector2(1792.0, 640.0), BattleVector2(48.0, 48.0)),
      AuthoritativeArenaObstacle("crate-east-bottom", "crate", BattleVector2(1792.0, 960.0), BattleVector2(48.0, 48.0))
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
    var lastValid = position
    val steps = math.ceil(math.max(0.0, distance) / 16.0).toInt
    var step = 1
    var hitBlocker = false

    while (step <= steps && !hitBlocker) {
      val travel = math.min(distance, step.toDouble * 16.0)
      val candidate = BattleVector2(
        x = position.x + direction.x * travel,
        y = position.y + direction.y * travel
      )

      if (!canOccupy(candidate, radius)) {
        hitBlocker = true
      } else {
        lastValid = candidate
      }
      step += 1
    }

    SteppedMotionResult(
      destination = lastValid,
      blocked = lastValid.x == position.x && lastValid.y == position.y,
      hitBlocker = hitBlocker
    )
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
