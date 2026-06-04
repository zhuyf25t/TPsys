package services.battle.microservices.world.objects.world

import services.battle.objects.core.{BattleMapId, BattleVector2}
import services.battle.microservices.abilities.objects.pickup.BattlePickupDefinition
import services.battle.microservices.extraction.objects.extraction.{
  BattleExtractionZoneDefinition,
  BattleGasPlanDefinition,
  BattleLootCacheDefinition
}

private[services] final case class BattleArenaContext(
  loadedMap: BattleLoadedMapSpec,
  worldRules: BattleWorldRuleConfig
) {
  def mapId: BattleMapId = loadedMap.mapId
  def themeId: String = loadedMap.themeId
  def worldSize: BattleVector2 = loadedMap.worldSize
  def floorTileSize: Int = worldRules.floorTileSize.value
  def borderObstacleSize: BattleVector2 = BattleVector2(floorTileSize.toDouble, floorTileSize.toDouble)
  def motionStepSize: Double = worldRules.motionStepSize.value
  def playerCollisionRadius: Double = worldRules.playerCollisionRadius.value
  def projectileBirthClearance: Double = worldRules.projectileBirthClearance.value
  def projectileShooterAdvantageRadius: Double = worldRules.projectileShooterAdvantageRadius.value
  def spawnPoints: Vector[BattleVector2] = loadedMap.spawnPoints
  def pickupDefinitions: Vector[BattlePickupDefinition] = loadedMap.pickupDefinitions
  def extractionZones: Vector[BattleExtractionZoneDefinition] = loadedMap.extractionZones
  def lootCaches: Vector[BattleLootCacheDefinition] = loadedMap.lootCaches
  def gasPlan: Option[BattleGasPlanDefinition] = loadedMap.gasPlan

  lazy val arenaObstacles: Vector[ArenaObstacle] =
    borderObstacles ++ loadedMap.collisionObstacles

  private def borderObstacles: Vector[ArenaObstacle] = {
    val halfTile = floorTileSize / 2
    val horizontal =
      (halfTile until worldSize.x.toInt by floorTileSize).toVector.flatMap { x =>
        Vector(
          ArenaObstacle.aabb(
            s"border-top-$x",
            ArenaObstacleKind.Wall,
            BattleVector2(x.toDouble, floorTileSize / 2.0),
            borderObstacleSize
          ),
          ArenaObstacle.aabb(
            s"border-bottom-$x",
            ArenaObstacleKind.Wall,
            BattleVector2(x.toDouble, worldSize.y - floorTileSize / 2.0),
            borderObstacleSize
          )
        )
      }
    val vertical =
      (floorTileSize + halfTile until worldSize.y.toInt - halfTile by floorTileSize).toVector.flatMap { y =>
        Vector(
          ArenaObstacle.aabb(
            s"border-left-$y",
            ArenaObstacleKind.Wall,
            BattleVector2(floorTileSize / 2.0, y.toDouble),
            borderObstacleSize
          ),
          ArenaObstacle.aabb(
            s"border-right-$y",
            ArenaObstacleKind.Wall,
            BattleVector2(worldSize.x - floorTileSize / 2.0, y.toDouble),
            borderObstacleSize
          )
        )
      }

    horizontal ++ vertical
  }
}

private[services] object BattleArenaContext {
  val ZeroVector: BattleVector2 = BattleVector2(0.0, 0.0)
}
