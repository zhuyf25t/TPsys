package services.battle.engine

import services.battle.objects.*

private[services] enum ArenaObstacleKind {
  case Wall
  case Crate
  case TreeTrunk
  case BuildingWall
  case Rock
  case Logs
  case Hay
  case Stump
}

private[services] enum ArenaObstacleShape {
  case Aabb(size: BattleVector2)
  case Circle(radius: Double)

  def boundsSize: BattleVector2 =
    this match {
      case ArenaObstacleShape.Aabb(size)     => size
      case ArenaObstacleShape.Circle(radius) => BattleVector2(radius * 2.0, radius * 2.0)
    }
}

private[services] final case class ArenaObstacle(
  obstacleId: String,
  kind: ArenaObstacleKind,
  position: BattleVector2,
  shape: ArenaObstacleShape
) {
  def size: BattleVector2 =
    shape.boundsSize
}

private[services] object ArenaObstacle {
  def aabb(
    obstacleId: String,
    kind: ArenaObstacleKind,
    position: BattleVector2,
    size: BattleVector2
  ): ArenaObstacle =
    ArenaObstacle(
      obstacleId = obstacleId,
      kind = kind,
      position = position,
      shape = ArenaObstacleShape.Aabb(size)
    )
}

private[services] object BattleArenaCatalog {
  private val DefaultMapId: BattleMapId = BattleMode.mapId(BattleMode.Default)
  private val activeMapId = ThreadLocal.withInitial[BattleMapId](() => DefaultMapId)

  def withMap[A](mapId: BattleMapId)(work: => A): A = {
    val previousMapId = activeMapId.get()
    activeMapId.set(mapId)
    try work
    finally activeMapId.set(previousMapId)
  }

  def loadedMap(mapId: BattleMapId): BattleLoadedMapSpec =
    BattleMapSpecLoader.load(mapId)

  private def activeMap: BattleLoadedMapSpec =
    BattleMapSpecLoader.load(activeMapId.get())

  def WorldSize: BattleVector2 = activeMap.worldSize
  val ZeroVector: BattleVector2 = BattleVector2(0.0, 0.0)
  val FloorTileSize: Int = 64
  val BorderObstacleSize: BattleVector2 = BattleVector2(FloorTileSize.toDouble, FloorTileSize.toDouble)
  val MotionStepSize: Double = 16.0
  def MapId: BattleMapId = activeMap.mapId
  def ThemeId: String = activeMap.themeId
  val PlayerCollisionRadius: Double = 18.0
  val ProjectileBirthClearance: Double = 4.0
  val ProjectileShooterAdvantageRadius: Double = 6.0
  def ArenaObstacles: Vector[ArenaObstacle] = borderObstacles ++ innerObstacles
  def SpawnPoints: Vector[BattleVector2] = activeMap.spawnPoints
  def PickupDefinitions: Vector[BattlePickupDefinition] = activeMap.pickupDefinitions

  private val WallObstacle: ArenaObstacleKind = ArenaObstacleKind.Wall

  private def borderObstacles: Vector[ArenaObstacle] = {
    val horizontal =
      (FloorTileSize / 2 until WorldSize.x.toInt by FloorTileSize).toVector.flatMap { x =>
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
      (FloorTileSize + FloorTileSize / 2 until WorldSize.y.toInt - FloorTileSize / 2 by FloorTileSize).toVector.flatMap { y =>
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
