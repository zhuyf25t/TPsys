package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object InMemoryBattleStateCatalog {
  private[services] final case class ArenaObstacle(
    obstacleId: String,
    kind: String,
    position: BattleVector2,
    size: BattleVector2
  )

  val DefaultBattleDuration: DurationMillis = DurationMillis(5L * 60L * 1000L)
  private[services] val TickStepMs: Long = 33L
  private[services] val WorldSize: BattleVector2 = BattleVector2(2560.0, 1600.0)
  private[services] val ZeroVector: BattleVector2 = BattleVector2(0.0, 0.0)
  private[services] val FloorTileSize: Int = 64
  private[services] val BorderObstacleSize: BattleVector2 = BattleVector2(FloorTileSize.toDouble, FloorTileSize.toDouble)
  private[services] val MotionStepSize: Double = 16.0
  private[services] val MapId: String = "default-industrial-arena"
  private[services] val ThemeId: String = "industrial"
  private[services] val WalkSpeed: Double = 255.0
  private[services] val SprintSpeed: Double = 446.25
  private[services] val BotMoveSpeed: Double = 108.0
  private[services] val StaminaDrainPerSecond: Double = 38.0
  private[services] val StaminaRecoverPerSecond: Double = 24.0
  private[services] val SlowFieldMovementFactor: Double = 0.5
  private[services] val SlowFieldProjectileFactor: Double = 0.5
  private[services] val PistolMagazineSize: Int = 12
  private[services] val InitialPistolReserveAmmo: Int = 48
  private[services] val PistolPickupAmmo: Int = 24
  private[services] val PistolFireCooldownMs: Int = 260
  private[services] val PistolReloadMs: Int = 1000
  private[services] val PistolDamage: Int = 12
  private[services] val PistolProjectileSpeed: Double = 1400.0
  private[services] val PistolProjectileRadius: Double = 8.0
  private[services] val PistolProjectileLifetimeMs: Long = 30000L
  private[services] val PistolRecoilStrength: Double = 20.0
  private[services] val RocketCooldownMs: Int = 160
  private[services] val RocketReloadMs: Int = 2500
  private[services] val RocketMagazineSize: Int = 1
  private[services] val RocketReserveAmmo: Int = 3
  private[services] val RocketPickupAmmo: Int = 1
  private[services] val RocketProjectileSpeed: Double = 340.0
  private[services] val RocketDamage: Int = 60
  private[services] val RocketProjectileLifetimeMs: Long = 30000L
  private[services] val RocketProjectileRadius: Double = 14.0
  private[services] val RocketSplashRadius: Double = 132.0
  private[services] val RocketRecoilStrength: Double = 120.0
  private[services] val GatlingCooldownMs: Int = 72
  private[services] val GatlingReloadMs: Int = 0
  private[services] val GatlingMagazineSize: Int = 0
  private[services] val GatlingPickupAmmo: Int = 0
  private[services] val GatlingProjectileSpeed: Double = 980.0
  private[services] val GatlingDamage: Int = 5
  private[services] val GatlingProjectileLifetimeMs: Long = 30000L
  private[services] val GatlingProjectileRadius: Double = 7.0
  private[services] val GatlingSpreadRadians: Double = 0.06
  private[services] val GatlingRecoilStrength: Double = 8.0
  private[services] val GatlingMaxHeat: Int = 100
  private[services] val GatlingHeatPerShot: Int = 8
  private[services] val GatlingCoolRatePerSecond: Int = 32
  private[services] val GatlingOverheatLockMs: Int = 1400
  private[services] val ShotgunCooldownMs: Int = 760
  private[services] val ShotgunReloadMs: Int = 1200
  private[services] val ShotgunMagazineSize: Int = 6
  private[services] val ShotgunReserveAmmo: Int = 18
  private[services] val ShotgunPickupAmmo: Int = 6
  private[services] val ShotgunProjectileSpeed: Double = 720.0
  private[services] val ShotgunDamage: Int = 8
  private[services] val ShotgunProjectileLifetimeMs: Long = 30000L
  private[services] val ShotgunProjectileRadius: Double = 7.0
  private[services] val ShotgunPellets: Int = 5
  private[services] val ShotgunSpreadRadians: Double = 0.42
  private[services] val ShotgunRecoilStrength: Double = 80.0
  private[services] val BlinkRange: Double = 250.0
  private[services] val BlinkCooldownMs: Int = 2200
  private[services] val BlinkActiveMs: Long = 240L
  private[services] val DashDistance: Double = 180.0
  private[services] val DashCooldownMs: Int = 5000
  private[services] val DashActiveMs: Long = 180L
  private[services] val FreezeRadius: Double = 150.0
  private[services] val FreezeCastRange: Double = 520.0
  private[services] val FreezeCooldownMs: Int = 12000
  private[services] val FreezeDurationMs: Long = 10000L
  private[services] val PickupContactRadius: Double = 40.0
  private[services] val PickupRespawnMs: Long = 10000L
  private[services] val MedkitHeal: Int = 25
  private[services] val RetainedProjectileTerminalCount: Int = 64
  private[services] val RetainedBattleEventCount: Int = 12
  private[services] val ReplayFrameSampleIntervalMs: Long = 1000L
  private[services] val RetainedReplayFrameCount: Int = 32
  private[services] val BotPreferredRange: Double = 260.0
  private[services] val BotFireRange: Double = 520.0
  private[services] val BotHumanFireRange: Double = 360.0
  private[services] val BotHumanOpeningFireDelayMs: Long = 15000L
  private[services] val PlayerCollisionRadius: Double = 18.0
  private[services] val ProjectileBirthClearance: Double = 4.0
  private[services] val ProjectileShooterAdvantageRadius: Double = 6.0
  private[services] val ArenaObstacles: Vector[ArenaObstacle] = borderObstacles ++ innerObstacles
  private[services] val SpawnPoints: Vector[BattleVector2] = Vector(
    BattleVector2(704.0, 800.0),
    BattleVector2(512.0, 544.0),
    BattleVector2(512.0, 1056.0),
    BattleVector2(1600.0, 320.0),
    BattleVector2(1600.0, 1280.0),
    BattleVector2(2048.0, 800.0)
  )

  private def borderObstacles: Vector[ArenaObstacle] = {
    val horizontal =
      (FloorTileSize / 2 until WorldSize.x.toInt by FloorTileSize).toVector.flatMap { x =>
        Vector(
          ArenaObstacle(s"border-top-$x", "wall", BattleVector2(x.toDouble, FloorTileSize / 2.0), BorderObstacleSize),
          ArenaObstacle(s"border-bottom-$x", "wall", BattleVector2(x.toDouble, WorldSize.y - FloorTileSize / 2.0), BorderObstacleSize)
        )
      }
    val vertical =
      (FloorTileSize + FloorTileSize / 2 until WorldSize.y.toInt - FloorTileSize / 2 by FloorTileSize).toVector.flatMap { y =>
        Vector(
          ArenaObstacle(s"border-left-$y", "wall", BattleVector2(FloorTileSize / 2.0, y.toDouble), BorderObstacleSize),
          ArenaObstacle(s"border-right-$y", "wall", BattleVector2(WorldSize.x - FloorTileSize / 2.0, y.toDouble), BorderObstacleSize)
        )
      }

    horizontal ++ vertical
  }

  private def innerObstacles: Vector[ArenaObstacle] =
    Vector(
      ArenaObstacle("cover-nw-1", "wall", BattleVector2(416.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-nw-2", "wall", BattleVector2(480.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-nw-3", "wall", BattleVector2(416.0, 480.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-ne-1", "wall", BattleVector2(2144.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-ne-2", "wall", BattleVector2(2080.0, 416.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-ne-3", "wall", BattleVector2(2144.0, 480.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-sw-1", "wall", BattleVector2(416.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-sw-2", "wall", BattleVector2(480.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-sw-3", "wall", BattleVector2(416.0, 1120.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-se-1", "wall", BattleVector2(2144.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-se-2", "wall", BattleVector2(2080.0, 1184.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("cover-se-3", "wall", BattleVector2(2144.0, 1120.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-1", "wall", BattleVector2(1184.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-2", "wall", BattleVector2(1248.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-3", "wall", BattleVector2(1312.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-top-4", "wall", BattleVector2(1376.0, 448.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-1", "wall", BattleVector2(1184.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-2", "wall", BattleVector2(1248.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-3", "wall", BattleVector2(1312.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("center-bot-4", "wall", BattleVector2(1376.0, 1152.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-1", "wall", BattleVector2(928.0, 640.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-2", "wall", BattleVector2(928.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-3", "wall", BattleVector2(928.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-left-4", "wall", BattleVector2(928.0, 960.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-1", "wall", BattleVector2(1632.0, 640.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-2", "wall", BattleVector2(1632.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-3", "wall", BattleVector2(1632.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-right-4", "wall", BattleVector2(1632.0, 960.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("crate-mid-top-left", "crate", BattleVector2(1184.0, 736.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-mid-top-right", "crate", BattleVector2(1376.0, 736.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-mid-bottom-left", "crate", BattleVector2(1184.0, 864.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-mid-bottom-right", "crate", BattleVector2(1376.0, 864.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("mid-west-1", "wall", BattleVector2(640.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("mid-west-2", "wall", BattleVector2(640.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("mid-east-1", "wall", BattleVector2(1920.0, 704.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("mid-east-2", "wall", BattleVector2(1920.0, 896.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-top-left", "wall", BattleVector2(1056.0, 608.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-top-right", "wall", BattleVector2(1504.0, 608.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-bottom-left", "wall", BattleVector2(1056.0, 992.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("lane-bottom-right", "wall", BattleVector2(1504.0, 992.0), BattleVector2(64.0, 64.0)),
      ArenaObstacle("crate-west-top", "crate", BattleVector2(768.0, 640.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-west-bottom", "crate", BattleVector2(768.0, 960.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-east-top", "crate", BattleVector2(1792.0, 640.0), BattleVector2(48.0, 48.0)),
      ArenaObstacle("crate-east-bottom", "crate", BattleVector2(1792.0, 960.0), BattleVector2(48.0, 48.0))
    )

}
