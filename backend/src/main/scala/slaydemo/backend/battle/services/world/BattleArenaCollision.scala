package slaydemo.backend.battle.services.world

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.BattleVector2

private[services] object BattleArenaCollision {
  /** 中文名：firstsegment世界exitt（firstSegmentWorldExitT）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def firstSegmentWorldExitT(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double
  ): Option[Double] = {
    val minX = radius
    val maxX = BattleArenaCatalog.WorldSize.x - radius
    val minY = radius
    val maxY = BattleArenaCatalog.WorldSize.y - radius

    if !isPointInAabb(start, minX, maxX, minY, maxY) then Some(0.0)
    else if isPointInAabb(end, minX, maxX, minY, maxY) then None
    else {
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
  }

  /** 中文名：firstsegmentobstacleentert（firstSegmentObstacleEnterT）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def firstSegmentObstacleEnterT(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double,
    obstacle: ArenaObstacle
  ): Option[Double] = {
    val minX = obstacle.position.x - obstacle.size.x / 2.0 - radius
    val maxX = obstacle.position.x + obstacle.size.x / 2.0 + radius
    val minY = obstacle.position.y - obstacle.size.y / 2.0 - radius
    val maxY = obstacle.position.y + obstacle.size.y / 2.0 + radius
    firstSegmentAabbEnterT(start, end, minX, maxX, minY, maxY)
  }

  /** 中文名：firstsegmentaabbentert（firstSegmentAabbEnterT）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def firstSegmentAabbEnterT(
    start: BattleVector2,
    end: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): Option[Double] =
    if isPointInAabb(start, minX, maxX, minY, maxY) then Some(0.0)
    else {
      val dx = end.x - start.x
      val dy = end.y - start.y
      val maybeXInterval = segmentAxisInterval(start.x, dx, minX, maxX)
      val maybeYInterval = segmentAxisInterval(start.y, dy, minY, maxY)

      (maybeXInterval, maybeYInterval) match {
        case (Some((xEnter, xExit)), Some((yEnter, yExit))) =>
          val enter = math.max(xEnter, yEnter)
          val exit = math.min(xExit, yExit)
          Option.when(enter <= exit && exit >= 0.0 && enter <= 1.0)(math.max(0.0, enter))
        case _ =>
          None
      }
    }

  /** 中文名：segmentaxisinterval（segmentAxisInterval）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def segmentAxisInterval(
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

  /** 中文名：判断是否pointinaabb（isPointInAabb）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def isPointInAabb(
    point: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): Boolean =
    point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY

  /** 中文名：segmentcirclehitt（segmentCircleHitT）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def segmentCircleHitT(
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

  /** 中文名：判断是否blockedpoint（isBlockedPoint）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def isBlockedPoint(point: BattleVector2): Boolean =
    !canPlayerOccupy(point, BattleArenaCatalog.PlayerCollisionRadius)

  /** 中文名：判断可否玩家occupy（canPlayerOccupy）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def canPlayerOccupy(point: BattleVector2, radius: Double): Boolean =
    isInWorld(point, radius) && !collidesWithArenaObstacles(point, radius)

  /** 中文名：collideswith竞技场obstacles（collidesWithArenaObstacles）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def collidesWithArenaObstacles(point: BattleVector2, radius: Double): Boolean =
    BattleArenaCatalog.ArenaObstacles.exists(obstacle => intersectsObstacle(point, radius, obstacle))

  /** 中文名：intersectsobstacle（intersectsObstacle）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def intersectsObstacle(
    point: BattleVector2,
    radius: Double,
    obstacle: ArenaObstacle
  ): Boolean = {
    val dx = math.abs(point.x - obstacle.position.x)
    val dy = math.abs(point.y - obstacle.position.y)
    dx < radius + obstacle.size.x / 2.0 && dy < radius + obstacle.size.y / 2.0
  }

  /** 中文名：判断是否in世界（isInWorld）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def isInWorld(point: BattleVector2, radius: Double): Boolean =
    point.x >= radius &&
      point.y >= radius &&
      point.x <= BattleArenaCatalog.WorldSize.x - radius &&
      point.y <= BattleArenaCatalog.WorldSize.y - radius

  /** 中文名：判断是否in世界（isInWorld）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def isInWorld(point: BattleVector2): Boolean =
    point.x >= 0.0 &&
      point.y >= 0.0 &&
      point.x <= BattleArenaCatalog.WorldSize.x &&
      point.y <= BattleArenaCatalog.WorldSize.y

  /** 中文名：clamp转为世界（clampToWorld）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def clampToWorld(point: BattleVector2): BattleVector2 =
    BattleVector2(
      math.max(0.0, math.min(BattleArenaCatalog.WorldSize.x, point.x)),
      math.max(0.0, math.min(BattleArenaCatalog.WorldSize.y, point.y))
    )
}
