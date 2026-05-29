package services.battle.microservices.world.services

import services.battle.objects.BattleVector2

private[battle] object BattleGeometry {
  def clampDouble(value: Double, minimum: Double, maximum: Double): Double =
    math.max(minimum, math.min(maximum, value))

  def add(left: BattleVector2, right: BattleVector2): BattleVector2 =
    BattleVector2(left.x + right.x, left.y + right.y)

  def subtract(left: BattleVector2, right: BattleVector2): BattleVector2 =
    BattleVector2(left.x - right.x, left.y - right.y)

  def scale(vector: BattleVector2, scalar: Double): BattleVector2 =
    BattleVector2(vector.x * scalar, vector.y * scalar)

  def pointAtSegmentT(start: BattleVector2, end: BattleVector2, t: Double): BattleVector2 = {
    val clampedT = clampDouble(t, 0.0, 1.0)
    BattleVector2(
      start.x + (end.x - start.x) * clampedT,
      start.y + (end.y - start.y) * clampedT
    )
  }

  def perpendicular(vector: BattleVector2, direction: Double): BattleVector2 =
    BattleVector2(-vector.y * direction, vector.x * direction)

  def dot(left: BattleVector2, right: BattleVector2): Double =
    left.x * right.x + left.y * right.y

  def vectorLength(vector: BattleVector2): Double =
    math.hypot(vector.x, vector.y)

  def distanceBetween(left: BattleVector2, right: BattleVector2): Double =
    math.hypot(left.x - right.x, left.y - right.y)
}
