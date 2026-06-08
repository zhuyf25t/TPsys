package services.battle.microservices.world.services

import cats.effect.IO

import services.battle.microservices.world.objects.world.{ArenaObstacle, ArenaObstacleShape, BattleArenaContext}
import services.battle.objects.BattleVector2

private[battle] object BattleArenaCollision {
  def firstSegmentWorldExitT(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double,
    arena: BattleArenaContext
  ): IO[Option[Double]] = {
    val minX = radius
    val maxX = arena.worldSize.x - radius
    val minY = radius
    val maxY = arena.worldSize.y - radius

    for
      startInside <- isPointInAabb(start, minX, maxX, minY, maxY)
      endInside <- isPointInAabb(end, minX, maxX, minY, maxY)
      result <-
        if !startInside then IO.pure(Some(0.0))
        else if endInside then IO.pure(None)
        else IO.pure {
          val dx = end.x - start.x
          val dy = end.y - start.y
          val exitX =
            if dx > 0.0 then Some((maxX - start.x) / dx)
            else if dx < 0.0 then Some((minX - start.x) / dx)
            else None
          val exitY =
            if dy > 0.0 then Some((maxY - start.y) / dy)
            else if dy < 0.0 then Some((minY - start.y) / dy)
            else None

          (exitX.toVector ++ exitY.toVector)
            .filter(t => t >= 0.0 && t <= 1.0)
            .minOption
        }
    yield result
  }

  def firstSegmentObstacleEnterT(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double,
    obstacle: ArenaObstacle
  ): IO[Option[Double]] =
    IO.pure(firstSegmentObstacleEnterTPure(start, end, radius, obstacle))

  def firstSegmentAabbEnterT(
    start: BattleVector2,
    end: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): IO[Option[Double]] =
    IO.pure(firstSegmentAabbEnterTPure(start, end, minX, maxX, minY, maxY))

  def segmentAxisInterval(
    start: Double,
    delta: Double,
    min: Double,
    max: Double
  ): IO[Option[(Double, Double)]] = IO.pure(segmentAxisIntervalPure(start, delta, min, max))

  def isPointInAabb(
    point: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): IO[Boolean] = IO.pure(isPointInAabbPure(point, minX, maxX, minY, maxY))

  def segmentCircleHitT(
    start: BattleVector2,
    end: BattleVector2,
    center: BattleVector2,
    radius: Double
  ): IO[Option[Double]] = IO.pure(segmentCircleHitTPure(start, end, center, radius))

  def isBlockedPoint(point: BattleVector2, arena: BattleArenaContext): IO[Boolean] =
    canPlayerOccupy(point, arena.playerCollisionRadius, arena).map(canOccupy => !canOccupy)

  def canPlayerOccupy(point: BattleVector2, radius: Double, arena: BattleArenaContext): IO[Boolean] =
    for
      inWorld <- isInWorld(point, radius, arena)
      collides <-
        if inWorld then collidesWithArenaObstacles(point, radius, arena)
        else IO.pure(false)
    yield inWorld && !collides

  def hasArenaLineOfSight(start: BattleVector2, end: BattleVector2, arena: BattleArenaContext): IO[Boolean] =
    IO.pure {
      arena.arenaObstacles.forall { obstacle =>
        firstSegmentObstacleEnterTPure(start, end, arena.projectileShooterAdvantageRadius, obstacle).isEmpty
      }
    }

  def collidesWithArenaObstacles(point: BattleVector2, radius: Double, arena: BattleArenaContext): IO[Boolean] =
    IO.pure(arena.arenaObstacles.exists(obstacle => intersectsObstaclePure(point, radius, obstacle)))

  def intersectsObstacle(
    point: BattleVector2,
    radius: Double,
    obstacle: ArenaObstacle
  ): IO[Boolean] = IO.pure(intersectsObstaclePure(point, radius, obstacle))

  private def firstSegmentObstacleEnterTPure(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double,
    obstacle: ArenaObstacle
  ): Option[Double] =
    obstacle.shape match {
      case ArenaObstacleShape.Aabb(size) =>
        val minX = obstacle.position.x - size.x / 2.0 - radius
        val maxX = obstacle.position.x + size.x / 2.0 + radius
        val minY = obstacle.position.y - size.y / 2.0 - radius
        val maxY = obstacle.position.y + size.y / 2.0 + radius
        firstSegmentAabbEnterTPure(start, end, minX, maxX, minY, maxY)
      case ArenaObstacleShape.Circle(obstacleRadius) =>
        segmentCircleHitTPure(start, end, obstacle.position, obstacleRadius + radius)
    }

  private def firstSegmentAabbEnterTPure(
    start: BattleVector2,
    end: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): Option[Double] =
    if isPointInAabbPure(start, minX, maxX, minY, maxY) then Some(0.0)
    else {
      val dx = end.x - start.x
      val dy = end.y - start.y
      (segmentAxisIntervalPure(start.x, dx, minX, maxX), segmentAxisIntervalPure(start.y, dy, minY, maxY)) match {
        case (Some((xEnter, xExit)), Some((yEnter, yExit))) =>
          val enter = math.max(xEnter, yEnter)
          val exit = math.min(xExit, yExit)
          Option.when(enter <= exit && exit >= 0.0 && enter <= 1.0)(math.max(0.0, enter))
        case _ =>
          None
      }
    }

  private def segmentAxisIntervalPure(
    start: Double,
    delta: Double,
    min: Double,
    max: Double
  ): Option[(Double, Double)] =
    if math.abs(delta) <= 0.000001 then
      Option.when(start >= min && start <= max)((Double.NegativeInfinity, Double.PositiveInfinity))
    else {
      val first = (min - start) / delta
      val second = (max - start) / delta
      Some(math.min(first, second) -> math.max(first, second))
    }

  private def isPointInAabbPure(
    point: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): Boolean =
    point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY

  private def segmentCircleHitTPure(
    start: BattleVector2,
    end: BattleVector2,
    center: BattleVector2,
    radius: Double
  ): Option[Double] = {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val radiusSquared = radius * radius
    val startToCenterX = start.x - center.x
    val startToCenterY = start.y - center.y

    if startToCenterX * startToCenterX + startToCenterY * startToCenterY <= radiusSquared then Some(0.0)
    else {
      val a = dx * dx + dy * dy
      if a <= 0.000001 then None
      else {
        val b = 2.0 * (startToCenterX * dx + startToCenterY * dy)
        val c = startToCenterX * startToCenterX + startToCenterY * startToCenterY - radiusSquared
        val discriminant = b * b - 4.0 * a * c

        if discriminant < 0.0 then None
        else {
          val sqrtDiscriminant = math.sqrt(discriminant)
          val firstT = (-b - sqrtDiscriminant) / (2.0 * a)
          val secondT = (-b + sqrtDiscriminant) / (2.0 * a)

          if firstT >= 0.0 && firstT <= 1.0 then Some(firstT)
          else if secondT >= 0.0 && secondT <= 1.0 then Some(secondT)
          else None
        }
      }
    }
  }

  private def intersectsObstaclePure(
    point: BattleVector2,
    radius: Double,
    obstacle: ArenaObstacle
  ): Boolean = {
    obstacle.shape match {
      case ArenaObstacleShape.Aabb(size) =>
        val dx = math.abs(point.x - obstacle.position.x)
        val dy = math.abs(point.y - obstacle.position.y)
        dx < radius + size.x / 2.0 && dy < radius + size.y / 2.0
      case ArenaObstacleShape.Circle(obstacleRadius) =>
        val dx = point.x - obstacle.position.x
        val dy = point.y - obstacle.position.y
        dx * dx + dy * dy < math.pow(radius + obstacleRadius, 2.0)
    }
  }

  def isInWorld(point: BattleVector2, radius: Double, arena: BattleArenaContext): IO[Boolean] = IO.pure(
    point.x >= radius &&
      point.y >= radius &&
      point.x <= arena.worldSize.x - radius &&
      point.y <= arena.worldSize.y - radius
  )

  def isInWorld(point: BattleVector2, arena: BattleArenaContext): IO[Boolean] = IO.pure(
    point.x >= 0.0 &&
      point.y >= 0.0 &&
      point.x <= arena.worldSize.x &&
      point.y <= arena.worldSize.y
  )

  def clampToWorld(point: BattleVector2, arena: BattleArenaContext): IO[BattleVector2] = IO.pure(
    BattleVector2(
      math.max(0.0, math.min(arena.worldSize.x, point.x)),
      math.max(0.0, math.min(arena.worldSize.y, point.y))
    )
  )
}
