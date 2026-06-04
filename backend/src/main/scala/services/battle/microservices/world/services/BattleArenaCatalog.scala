package services.battle.microservices.world.services

import cats.effect.IO

import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.world.objects.world.{BattleArenaContext, BattleLoadedMapSpec}
import services.battle.objects.core.{BattleMapId, BattleVector2}

private[battle] object BattleArenaCatalog {
  val ZeroVector: BattleVector2 = BattleArenaContext.ZeroVector

  def loadedMapIO(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[BattleLoadedMapSpec] =
    battleRules.loadedMap(mapId)

  def contextFor(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[BattleArenaContext] =
    for
      loadedMap <- loadedMapIO(mapId, battleRules)
      worldRules <- battleRules.world
    yield BattleArenaContext(loadedMap, worldRules)
}
