package services.battle.microservices.world.services

import cats.effect.IO

import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.objects.{BattleMapId, BattleVector2, SpawnPointIndex}

private[battle] object BattleInitialLayout {
  def spawnPointFor(mapId: BattleMapId, index: SpawnPointIndex, battleRules: BattleDynamicRuleBook): IO[BattleVector2] =
    BattleArenaCatalog.loadedMapIO(mapId, battleRules).map { loadedMap =>
      loadedMap.spawnPoints.lift(index.value).getOrElse {
        throw IllegalStateException(s"Missing spawn point ${index.value} in PostgreSQL battle_world_map_rules.")
      }
    }

  def initialPickups(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[Vector[BattlePickupState]] =
    BattleArenaCatalog.loadedMapIO(mapId, battleRules).map(_.pickupDefinitions.map(_.initialState))
}
