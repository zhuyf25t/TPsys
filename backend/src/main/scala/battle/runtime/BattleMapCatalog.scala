package slaydemo.backend.battle.runtime

import slaydemo.backend.battle.objects.BattleVector2

object BattleMapCatalog {
  final case class ArenaObstacleDefinition(
    obstacleId: String,
    kind: String,
    position: BattleVector2,
    size: BattleVector2
  )

  final case class WeaponPickupDefinition(
    pickupId: String,
    weaponKind: String,
    position: BattleVector2
  )

  final case class ItemPickupDefinition(
    pickupId: String,
    kind: String,
    position: BattleVector2
  )

  final case class BattleMapConfig(
    mapId: String,
    displayName: String,
    themeId: String,
    worldSize: BattleVector2,
    heroSpawnPoints: Vector[BattleVector2],
    innerObstacles: Vector[ArenaObstacleDefinition],
    weaponPickupDefinitions: Vector[WeaponPickupDefinition],
    itemPickupDefinitions: Vector[ItemPickupDefinition]
  )

  val defaultMap: BattleMapConfig = BattleMapConfig(
    mapId = "default-industrial-arena",
    displayName = "默认工业竞技场",
    themeId = "industrial",
    worldSize = BattleVector2(2560.0, 1600.0),
    heroSpawnPoints = Vector(
      BattleVector2(704.0, 800.0),
      BattleVector2(512.0, 544.0),
      BattleVector2(512.0, 1056.0),
      BattleVector2(1600.0, 320.0),
      BattleVector2(1600.0, 1280.0),
      BattleVector2(2048.0, 800.0)
    ),
    innerObstacles = Vector(
      ArenaObstacleDefinition("cover-nw-1", "wall", BattleVector2(416.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-nw-2", "wall", BattleVector2(480.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-nw-3", "wall", BattleVector2(416.0, 480.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-ne-1", "wall", BattleVector2(2144.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-ne-2", "wall", BattleVector2(2080.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-ne-3", "wall", BattleVector2(2144.0, 480.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-sw-1", "wall", BattleVector2(416.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-sw-2", "wall", BattleVector2(480.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-sw-3", "wall", BattleVector2(416.0, 1120.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-se-1", "wall", BattleVector2(2144.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-se-2", "wall", BattleVector2(2080.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("cover-se-3", "wall", BattleVector2(2144.0, 1120.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-top-1", "wall", BattleVector2(1184.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-top-2", "wall", BattleVector2(1248.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-top-3", "wall", BattleVector2(1312.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-top-4", "wall", BattleVector2(1376.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-bot-1", "wall", BattleVector2(1184.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-bot-2", "wall", BattleVector2(1248.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-bot-3", "wall", BattleVector2(1312.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("center-bot-4", "wall", BattleVector2(1376.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-left-1", "wall", BattleVector2(928.0, 640.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-left-2", "wall", BattleVector2(928.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-left-3", "wall", BattleVector2(928.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-left-4", "wall", BattleVector2(928.0, 960.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-right-1", "wall", BattleVector2(1632.0, 640.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-right-2", "wall", BattleVector2(1632.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-right-3", "wall", BattleVector2(1632.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-right-4", "wall", BattleVector2(1632.0, 960.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("crate-mid-top-left", "crate", BattleVector2(1184.0, 736.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("crate-mid-top-right", "crate", BattleVector2(1376.0, 736.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("crate-mid-bottom-left", "crate", BattleVector2(1184.0, 864.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("crate-mid-bottom-right", "crate", BattleVector2(1376.0, 864.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("mid-west-1", "wall", BattleVector2(640.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("mid-west-2", "wall", BattleVector2(640.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("mid-east-1", "wall", BattleVector2(1920.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("mid-east-2", "wall", BattleVector2(1920.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-top-left", "wall", BattleVector2(1056.0, 608.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-top-right", "wall", BattleVector2(1504.0, 608.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-bottom-left", "wall", BattleVector2(1056.0, 992.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("lane-bottom-right", "wall", BattleVector2(1504.0, 992.0), BattleVector2(64.0, 64.0)),
      ArenaObstacleDefinition("crate-west-top", "crate", BattleVector2(768.0, 640.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("crate-west-bottom", "crate", BattleVector2(768.0, 960.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("crate-east-top", "crate", BattleVector2(1792.0, 640.0), BattleVector2(48.0, 48.0)),
      ArenaObstacleDefinition("crate-east-bottom", "crate", BattleVector2(1792.0, 960.0), BattleVector2(48.0, 48.0))
    ),
    weaponPickupDefinitions = Vector(
      WeaponPickupDefinition("pickup-rocket-1", "RocketLauncher", BattleVector2(1280.0, 256.0)),
      WeaponPickupDefinition("pickup-gatling-1", "Gatling", BattleVector2(704.0, 800.0)),
      WeaponPickupDefinition("pickup-shotgun-1", "Shotgun", BattleVector2(1856.0, 800.0)),
      WeaponPickupDefinition("pickup-rocket-2", "RocketLauncher", BattleVector2(1280.0, 1344.0)),
      WeaponPickupDefinition("pickup-gatling-2", "Gatling", BattleVector2(448.0, 800.0)),
      WeaponPickupDefinition("pickup-shotgun-2", "Shotgun", BattleVector2(2112.0, 800.0))
    ),
    itemPickupDefinitions = Vector(
      ItemPickupDefinition("pickup-medkit-1", "Medkit", BattleVector2(960.0, 608.0)),
      ItemPickupDefinition("pickup-medkit-2", "Medkit", BattleVector2(1600.0, 992.0))
    )
  )
}
