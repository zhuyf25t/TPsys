package slaydemo.backend.battle.runtime

import slaydemo.backend.battle.objects.BattleVector2
import slaydemo.backend.battle.rules.BattleRules

object BattleContentCatalog {
  final case class WeaponDefinition(
    weaponKind: String,
    projectileKind: String,
    cooldownMs: Long,
    reloadMs: Long,
    projectileSpeedPerSecond: Double,
    projectileDamage: Int,
    projectileLifetimeMs: Long,
    projectileRadius: Double,
    splashRadius: Double,
    pellets: Int,
    spreadRadians: Double,
    magazineSize: Int,
    reserveAmmo: Int,
    pickupAmmo: Int,
    recoilStrength: Double
  )

  final case class WeaponPickupDefinition(
    pickupId: String,
    weaponKind: String,
    position: BattleVector2
  )

  val DefaultBattleDurationMs: Long = BattleRules.BattleDurationMs

  val spawnPoints: Vector[BattleVector2] = Vector(
    BattleVector2(704.0, 800.0),
    BattleVector2(512.0, 544.0),
    BattleVector2(512.0, 1056.0),
    BattleVector2(1600.0, 320.0),
    BattleVector2(1600.0, 1280.0),
    BattleVector2(2048.0, 800.0)
  )

  val playerMoveSpeedPerSecond: Double = 255.0
  val playerSprintMultiplier: Double = 1.75
  val botMoveSpeedPerSecond: Double = 108.0
  val defaultProjectileRadius: Double = AuthoritativeArenaGeometry.ProjectileRadius
  val projectileShooterAdvantageRadius: Double = 6.0

  val pistolWeaponKind: String = "Pistol"
  val rocketLauncherWeaponKind: String = "RocketLauncher"
  val gatlingWeaponKind: String = "Gatling"
  val shotgunWeaponKind: String = "Shotgun"

  val weaponDefinitions: Map[String, WeaponDefinition] = Map(
    pistolWeaponKind -> WeaponDefinition(
      weaponKind = pistolWeaponKind,
      projectileKind = "pistol-bullet",
      cooldownMs = 260L,
      reloadMs = 1000L,
      projectileSpeedPerSecond = 920.0,
      projectileDamage = 12,
      projectileLifetimeMs = 900L,
      projectileRadius = defaultProjectileRadius,
      splashRadius = 0.0,
      pellets = 1,
      spreadRadians = 0.0,
      magazineSize = 12,
      reserveAmmo = 48,
      pickupAmmo = 24,
      recoilStrength = 20.0
    ),
    rocketLauncherWeaponKind -> WeaponDefinition(
      weaponKind = rocketLauncherWeaponKind,
      projectileKind = "rocket",
      cooldownMs = 160L,
      reloadMs = 2500L,
      projectileSpeedPerSecond = 340.0,
      projectileDamage = 60,
      projectileLifetimeMs = 2200L,
      projectileRadius = 14.0,
      splashRadius = 132.0,
      pellets = 1,
      spreadRadians = 0.0,
      magazineSize = 1,
      reserveAmmo = 3,
      pickupAmmo = 1,
      recoilStrength = 120.0
    ),
    gatlingWeaponKind -> WeaponDefinition(
      weaponKind = gatlingWeaponKind,
      projectileKind = "gatling-bullet",
      cooldownMs = 72L,
      reloadMs = 0L,
      projectileSpeedPerSecond = 980.0,
      projectileDamage = 5,
      projectileLifetimeMs = 620L,
      projectileRadius = 7.0,
      splashRadius = 0.0,
      pellets = 1,
      spreadRadians = 0.06,
      magazineSize = 100,
      reserveAmmo = 0,
      pickupAmmo = 0,
      recoilStrength = 8.0
    ),
    shotgunWeaponKind -> WeaponDefinition(
      weaponKind = shotgunWeaponKind,
      projectileKind = "shotgun-pellet",
      cooldownMs = 760L,
      reloadMs = 1200L,
      projectileSpeedPerSecond = 720.0,
      projectileDamage = 8,
      projectileLifetimeMs = 330L,
      projectileRadius = 7.0,
      splashRadius = 0.0,
      pellets = 5,
      spreadRadians = 0.42,
      magazineSize = 6,
      reserveAmmo = 18,
      pickupAmmo = 6,
      recoilStrength = 80.0
    )
  )

  val playerHitRadius: Double = AuthoritativeArenaGeometry.HeroRadius

  val medkitPickupId: String = "pickup-medkit-1"
  val medkitPickupKind: String = "Medkit"
  val medkitPickupPosition: BattleVector2 = BattleVector2(960.0, 608.0)
  val medkitPickupRadius: Double = 40.0
  val medkitHealAmount: Int = 25
  val medkitRespawnMs: Long = 10000L

  val weaponPickupKind: String = "Weapon"
  val weaponPickupRadius: Double = 40.0
  val weaponPickupRespawnMs: Long = 10000L
  val weaponPickupDefinitions: Vector[WeaponPickupDefinition] = Vector(
    WeaponPickupDefinition("pickup-rocket-1", rocketLauncherWeaponKind, BattleVector2(1280.0, 256.0)),
    WeaponPickupDefinition("pickup-gatling-1", gatlingWeaponKind, BattleVector2(704.0, 800.0)),
    WeaponPickupDefinition("pickup-shotgun-1", shotgunWeaponKind, BattleVector2(1856.0, 800.0)),
    WeaponPickupDefinition("pickup-rocket-2", rocketLauncherWeaponKind, BattleVector2(1280.0, 1344.0)),
    WeaponPickupDefinition("pickup-gatling-2", gatlingWeaponKind, BattleVector2(448.0, 800.0)),
    WeaponPickupDefinition("pickup-shotgun-2", shotgunWeaponKind, BattleVector2(2112.0, 800.0))
  )

  val dashSkillKind: String = "Dash"
  val dashDistance: Double = 180.0
  val dashCooldownMs: Long = 5000L
  val dashActiveMs: Long = 180L

  val blinkSkillKind: String = "Blink"
  val blinkRange: Double = 250.0
  val blinkCooldownMs: Long = 2200L
  val blinkActiveMs: Long = 240L

  val freezeSkillKind: String = "Freeze"
  val freezeRange: Double = 520.0
  val freezeRadius: Double = 150.0
  val freezeDurationMs: Long = 10000L
  val freezeCooldownMs: Long = 12000L
  val freezeSpeedMultiplier: Double = 0.5

  val botPreferredRange: Double = 260.0
  val botFireRange: Double = 520.0
  val botHumanFireRange: Double = 360.0
  val botHumanOpeningFireDelayMs: Long = 15000L

  val authoritativeWorldSize: BattleVector2 = AuthoritativeArenaGeometry.WorldSize
  val maxWorldX: Double = authoritativeWorldSize.x
  val maxWorldY: Double = authoritativeWorldSize.y

  val defaultMaxHp: Int = 100
  val playerMaxStamina: Double = 100.0
  val staminaDrainPerSecond: Double = 38.0
  val staminaRecoverPerSecond: Double = 24.0

  val retainedEventCount: Int = 12
  val retainedProjectileTerminalCount: Int = 64
  val replayFrameSampleIntervalMs: Long = 1000L
  val retainedReplayFrameCount: Int = 32
}
