package services.battle.microservices.world.services

import services.battle.microservices.world.database.BattleWorldRuleBook
import services.battle.microservices.world.objects.world.*
import services.battle.objects.core.{BattleMapId, BattleVector2}
import services.battle.microservices.abilities.objects.pickup.BattlePickupDefinition

private[battle] object BattleArenaCatalog {
  private val activeMapId = ThreadLocal[BattleMapId]()

  def withMap[A](mapId: BattleMapId)(work: => A): A = {
    val previousMapId = Option(activeMapId.get())
    activeMapId.set(mapId)
    try work
    finally {
      previousMapId match {
        case Some(value) => activeMapId.set(value)
        case None        => activeMapId.remove()
      }
    }
  }

  def loadedMap(mapId: BattleMapId): BattleLoadedMapSpec =
    BattleWorldRuleBook.loadedMap(mapId)

  private def activeMap: BattleLoadedMapSpec =
    Option(activeMapId.get())
      .map(BattleWorldRuleBook.loadedMap)
      .getOrElse(throw IllegalStateException("Battle map context was not selected before reading world rules."))

  def WorldSize: BattleVector2 = activeMap.worldSize
  val ZeroVector: BattleVector2 = BattleVector2(0.0, 0.0)
  def FloorTileSize: Int = BattleWorldRuleBook.world.floorTileSize.value
  def BorderObstacleSize: BattleVector2 = BattleVector2(FloorTileSize.toDouble, FloorTileSize.toDouble)
  def MotionStepSize: Double = BattleWorldRuleBook.world.motionStepSize.value
  def MapId: BattleMapId = activeMap.mapId
  def ThemeId: String = activeMap.themeId
  def PlayerCollisionRadius: Double = BattleWorldRuleBook.world.playerCollisionRadius.value
  def ProjectileBirthClearance: Double = BattleWorldRuleBook.world.projectileBirthClearance.value
  def ProjectileShooterAdvantageRadius: Double = BattleWorldRuleBook.world.projectileShooterAdvantageRadius.value
  def ArenaObstacles: Vector[ArenaObstacle] = borderObstacles ++ innerObstacles
  def SpawnPoints: Vector[BattleVector2] = activeMap.spawnPoints
  def PickupDefinitions: Vector[BattlePickupDefinition] = activeMap.pickupDefinitions

  private val WallObstacle: ArenaObstacleKind = ArenaObstacleKind.Wall

  private def borderObstacles: Vector[ArenaObstacle] = {
    val halfTile = FloorTileSize / 2
    val horizontal =
      (halfTile until WorldSize.x.toInt by FloorTileSize).toVector.flatMap { x =>
        Vector(
          ArenaObstacle.aabb(
            s"border-top-$x",
            WallObstacle,
            BattleVector2(x.toDouble, FloorTileSize / 2.0),
            BorderObstacleSize
          ),
          ArenaObstacle.aabb(
            s"border-bottom-$x",
            WallObstacle,
            BattleVector2(x.toDouble, WorldSize.y - FloorTileSize / 2.0),
            BorderObstacleSize
          )
        )
      }
    val vertical =
      (FloorTileSize + halfTile until WorldSize.y.toInt - halfTile by FloorTileSize).toVector.flatMap { y =>
        Vector(
          ArenaObstacle.aabb(
            s"border-left-$y",
            WallObstacle,
            BattleVector2(FloorTileSize / 2.0, y.toDouble),
            BorderObstacleSize
          ),
          ArenaObstacle.aabb(
            s"border-right-$y",
            WallObstacle,
            BattleVector2(WorldSize.x - FloorTileSize / 2.0, y.toDouble),
            BorderObstacleSize
          )
        )
      }

    horizontal ++ vertical
  }

  private def innerObstacles: Vector[ArenaObstacle] =
    activeMap.collisionObstacles
}
