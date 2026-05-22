package services.battle.services.world

import services.battle.services.*

import services.battle.objects.*

private[services] object BattleInitialLayout {
  /** 中文名：spawnpointfor（spawnPointFor）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def spawnPointFor(index: SpawnPointIndex): BattleVector2 =
    BattleArenaCatalog.SpawnPoints.lift(index.value).getOrElse {
      val fallbackX = 240.0 + (index.value % 3) * 320.0
      val fallbackY = 240.0 + (index.value / 3) * 260.0
      BattleVector2(fallbackX, fallbackY)
    }

  /** 中文名：initialpickups（initialPickups）。游戏职责：在后端世界域中管理地图、碰撞、几何、移动和出生点，约束实体在战场中的空间行为。 */
  def initialPickups: Vector[BattlePickupState] =
    BattlePickupCatalog.initialPickups
}
