package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleInitialLayout {
  def spawnPointFor(index: SpawnPointIndex): BattleVector2 =
    BattleArenaCatalog.SpawnPoints.lift(index.value).getOrElse {
      val fallbackX = 240.0 + (index.value % 3) * 320.0
      val fallbackY = 240.0 + (index.value / 3) * 260.0
      BattleVector2(fallbackX, fallbackY)
    }

  def initialPickups: Vector[BattlePickupState] =
    BattlePickupCatalog.initialPickups
}
