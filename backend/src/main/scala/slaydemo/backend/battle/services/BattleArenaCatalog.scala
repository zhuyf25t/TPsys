package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] enum ArenaObstacleKind {
  case Wall
  case Crate
}

private[services] final case class ArenaObstacle(
  obstacleId: String,
  kind: ArenaObstacleKind,
  position: BattleVector2,
  size: BattleVector2
)

private[services] object BattleArenaCatalog {
  val WorldSize: BattleVector2 = BattleVector2(2560.0, 1600.0)
  val ZeroVector: BattleVector2 = BattleVector2(0.0, 0.0)
  val FloorTileSize: Int = 64
  val BorderObstacleSize: BattleVector2 = BattleVector2(FloorTileSize.toDouble, FloorTileSize.toDouble)
  val MotionStepSize: Double = 16.0
  val MapId: String = "default-industrial-arena"
  val ThemeId: String = "industrial"
  val PlayerCollisionRadius: Double = 18.0
  val ProjectileBirthClearance: Double = 4.0
  val ProjectileShooterAdvantageRadius: Double = 6.0
  val ArenaObstacles: Vector[ArenaObstacle] = borderObstacles ++ innerObstacles
  val SpawnPoints: Vector[BattleVector2] = Vector(
    BattleVector2(704.0, 800.0),
    BattleVector2(512.0, 544.0),
    BattleVector2(512.0, 1056.0),
    BattleVector2(1600.0, 320.0),
    BattleVector2(1600.0, 1280.0),
    BattleVector2(2048.0, 800.0)
  )

  private val WallObstacle: ArenaObstacleKind = ArenaObstacleKind.Wall
  private val CrateObstacle: ArenaObstacleKind = ArenaObstacleKind.Crate

  private def borderObstacles: Vector[ArenaObstacle] = {
    val horizontal =
      (FloorTileSize / 2 until WorldSize.x.toInt by FloorTileSize).toVector.flatMap { x =>
        Vector(
          ArenaObstacle(s"border-top-$x", WallObstacle, BattleVector2(x.toDouble, FloorTileSize / 2.0), BorderObstacleSize),
          ArenaObstacle(s"border-bottom-$x", WallObstacle, BattleVector2(x.toDouble, WorldSize.y - FloorTileSize / 2.0), BorderObstacleSize)
        )
      }
    val vertical =
      (FloorTileSize + FloorTileSize / 2 until WorldSize.y.toInt - FloorTileSize / 2 by FloorTileSize).toVector.flatMap { y =>
        Vector(
          ArenaObstacle(s"border-left-$y", WallObstacle, BattleVector2(FloorTileSize / 2.0, y.toDouble), BorderObstacleSize),
          ArenaObstacle(s"border-right-$y", WallObstacle, BattleVector2(WorldSize.x - FloorTileSize / 2.0, y.toDouble), BorderObstacleSize)
        )
      }

    horizontal ++ vertical
  }

  private def innerObstacles: Vector[ArenaObstacle] =
    Vector(
      ArenaObstacle("cover-nw-1", WallObstacle, BattleVector2(416.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-nw-2", WallObstacle, BattleVector2(480.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-nw-3", WallObstacle, BattleVector2(416.0, 480.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-ne-1", WallObstacle, BattleVector2(2144.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-ne-2", WallObstacle, BattleVector2(2080.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-ne-3", WallObstacle, BattleVector2(2144.0, 480.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-sw-1", WallObstacle, BattleVector2(416.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-sw-2", WallObstacle, BattleVector2(480.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-sw-3", WallObstacle, BattleVector2(416.0, 1120.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-se-1", WallObstacle, BattleVector2(2144.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-se-2", WallObstacle, BattleVector2(2080.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-se-3", WallObstacle, BattleVector2(2144.0, 1120.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-1", WallObstacle, BattleVector2(1184.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-2", WallObstacle, BattleVector2(1248.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-3", WallObstacle, BattleVector2(1312.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-4", WallObstacle, BattleVector2(1376.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-1", WallObstacle, BattleVector2(1184.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-2", WallObstacle, BattleVector2(1248.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-3", WallObstacle, BattleVector2(1312.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-4", WallObstacle, BattleVector2(1376.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-1", WallObstacle, BattleVector2(928.0, 640.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-2", WallObstacle, BattleVector2(928.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-3", WallObstacle, BattleVector2(928.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-4", WallObstacle, BattleVector2(928.0, 960.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-1", WallObstacle, BattleVector2(1632.0, 640.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-2", WallObstacle, BattleVector2(1632.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-3", WallObstacle, BattleVector2(1632.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-4", WallObstacle, BattleVector2(1632.0, 960.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("crate-mid-top-left", CrateObstacle, BattleVector2(1184.0, 736.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-mid-top-right", CrateObstacle, BattleVector2(1376.0, 736.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-mid-bottom-left", CrateObstacle, BattleVector2(1184.0, 864.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-mid-bottom-right", CrateObstacle, BattleVector2(1376.0, 864.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("mid-west-1", WallObstacle, BattleVector2(640.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("mid-west-2", WallObstacle, BattleVector2(640.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("mid-east-1", WallObstacle, BattleVector2(1920.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("mid-east-2", WallObstacle, BattleVector2(1920.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-top-left", WallObstacle, BattleVector2(1056.0, 608.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-top-right", WallObstacle, BattleVector2(1504.0, 608.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-bottom-left", WallObstacle, BattleVector2(1056.0, 992.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-bottom-right", WallObstacle, BattleVector2(1504.0, 992.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("crate-west-top", CrateObstacle, BattleVector2(768.0, 640.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-west-bottom", CrateObstacle, BattleVector2(768.0, 960.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-east-top", CrateObstacle, BattleVector2(1792.0, 640.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-east-bottom", CrateObstacle, BattleVector2(1792.0, 960.0), BattleVector2(48.0, 48.0))
    )
}
