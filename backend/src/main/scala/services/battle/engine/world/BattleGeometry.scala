package services.battle.engine


import services.battle.objects.BattleVector2

private[services] object BattleGeometry {
  /** 中文名：clampdouble（clampDouble）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def clampDouble(value: Double, minimum: Double, maximum: Double): Double =
    math.max(minimum, math.min(maximum, value))

  /** 中文名：add（add）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def add(left: BattleVector2, right: BattleVector2): BattleVector2 =
    BattleVector2(left.x + right.x, left.y + right.y)

  /** 中文名：subtract（subtract）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def subtract(left: BattleVector2, right: BattleVector2): BattleVector2 =
    BattleVector2(left.x - right.x, left.y - right.y)

  /** 中文名：scale（scale）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def scale(vector: BattleVector2, scalar: Double): BattleVector2 =
    BattleVector2(vector.x * scalar, vector.y * scalar)

  /** 中文名：pointatsegmentt（pointAtSegmentT）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def pointAtSegmentT(start: BattleVector2, end: BattleVector2, t: Double): BattleVector2 = {
    val clampedT = clampDouble(t, 0.0, 1.0)
    BattleVector2(
      start.x + (end.x - start.x) * clampedT,
      start.y + (end.y - start.y) * clampedT
    )
  }

  /** 中文名：perpendicular（perpendicular）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def perpendicular(vector: BattleVector2, direction: Double): BattleVector2 =
    BattleVector2(-vector.y * direction, vector.x * direction)

  /** 中文名：dot（dot）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def dot(left: BattleVector2, right: BattleVector2): Double =
    left.x * right.x + left.y * right.y

  /** 中文名：vectorlength（vectorLength）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def vectorLength(vector: BattleVector2): Double =
    math.hypot(vector.x, vector.y)

  /** 中文名：distancebetween（distanceBetween）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def distanceBetween(left: BattleVector2, right: BattleVector2): Double =
    math.hypot(left.x - right.x, left.y - right.y)
}
