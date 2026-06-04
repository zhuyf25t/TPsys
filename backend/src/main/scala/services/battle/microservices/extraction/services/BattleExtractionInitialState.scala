package services.battle.microservices.extraction.services

import cats.effect.IO

import services.battle.microservices.extraction.objects.extraction.{
  BattleExtractionState,
  BattleExtractionStatus,
  BattleGasPhase,
  BattleGasPlanDefinition,
  BattleGasZoneState,
  BattleLootCacheState,
  BattleLootCacheStatus
}
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.world.services.BattleArenaCatalog
import services.battle.objects.core.{BattleMapId, DurationMillis, ElapsedMillis}

private[battle] object BattleExtractionInitialState {
  def gasZone(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[Option[BattleGasZoneState]] =
    BattleArenaCatalog.loadedMapIO(mapId, battleRules).flatMap { loadedMap =>
      loadedMap.gasPlan match {
        case Some(plan) => initialGasZone(plan)
        case None       => IO.pure(None)
      }
    }

  def extraction(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[Option[BattleExtractionState]] =
    BattleArenaCatalog.loadedMapIO(mapId, battleRules).map { loadedMap =>
      Option.when(loadedMap.extractionZones.nonEmpty) {
      BattleExtractionState(
        zones = loadedMap.extractionZones,
        status = BattleExtractionStatus.Inactive
      )
      }
    }

  def lootCaches(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[Vector[BattleLootCacheState]] =
    BattleArenaCatalog.loadedMapIO(mapId, battleRules).map { loadedMap =>
      loadedMap.lootCaches.map(cache =>
      BattleLootCacheState(
        cacheId = cache.cacheId,
        position = cache.position,
        radius = cache.radius,
        searchDuration = cache.searchDuration,
        scoreValue = cache.scoreValue,
        status = BattleLootCacheStatus.Available
      )
      )
    }

  private def initialGasZone(plan: BattleGasPlanDefinition): IO[Option[BattleGasZoneState]] =
    IO.pure(plan.stages.headOption.map(stage =>
      BattleGasZoneState(
        phase = BattleGasPhase.Waiting,
        center = plan.center,
        radius = stage.fromRadius,
        nextRadius = stage.toRadius,
        damagePerSecond = services.battle.microservices.extraction.objects.extraction.BattleGasDamagePerSecond(0.0),
        stageIndex = stage.stageIndex,
        progressMs = DurationMillis(0L),
        startsAt = stage.startsAt,
        endsAt = ElapsedMillis(stage.startsAt.value + stage.duration.value)
      )
    ))
}
