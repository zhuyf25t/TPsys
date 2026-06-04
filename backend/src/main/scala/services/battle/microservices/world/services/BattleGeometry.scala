package services.battle.microservices.world.services

import cats.effect.IO

import services.battle.objects.BattleVector2

private[battle] object BattleGeometry {
  def clampDouble(value: Double, minimum: Double, maximum: Double): IO[Double] =
    IO.pure(math.max(minimum, math.min(maximum, value)))

  def add(left: BattleVector2, right: BattleVector2): IO[BattleVector2] =
    IO.pure(BattleVector2(left.x + right.x, left.y + right.y))

  def subtract(left: BattleVector2, right: BattleVector2): IO[BattleVector2] =
    IO.pure(BattleVector2(left.x - right.x, left.y - right.y))

  def scale(vector: BattleVector2, scalar: Double): IO[BattleVector2] =
    IO.pure(BattleVector2(vector.x * scalar, vector.y * scalar))

  def pointAtSegmentT(start: BattleVector2, end: BattleVector2, t: Double): IO[BattleVector2] =
    clampDouble(t, 0.0, 1.0).map { clampedT =>
      BattleVector2(
        start.x + (end.x - start.x) * clampedT,
        start.y + (end.y - start.y) * clampedT
      )
    }

  def perpendicular(vector: BattleVector2, direction: Double): IO[BattleVector2] =
    IO.pure(BattleVector2(-vector.y * direction, vector.x * direction))

  def dot(left: BattleVector2, right: BattleVector2): IO[Double] =
    IO.pure(left.x * right.x + left.y * right.y)

  def vectorLength(vector: BattleVector2): IO[Double] =
    IO.pure(math.hypot(vector.x, vector.y))

  def distanceBetween(left: BattleVector2, right: BattleVector2): IO[Double] =
    IO.pure(math.hypot(left.x - right.x, left.y - right.y))
}
