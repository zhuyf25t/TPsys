package services.battle.microservices.world.services

import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.objects.{BattleVector2, SpawnPointIndex}

private[battle] object BattleInitialLayout {
  def spawnPointFor(index: SpawnPointIndex): BattleVector2 =
    BattleArenaCatalog.SpawnPoints.lift(index.value).getOrElse {
      throw IllegalStateException(s"Missing spawn point ${index.value} in PostgreSQL battle_world_map_rules.")
    }

  def initialPickups: Vector[BattlePickupState] =
    BattleArenaCatalog.PickupDefinitions.map(_.initialState)
}
