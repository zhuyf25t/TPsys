package slaydemo.backend.battle.runtime

import slaydemo.backend.battle.objects.BattleVector2
import slaydemo.backend.battle.rules.BattleRules

object BattleContentCatalog {
  type WeaponPickupDefinition = BattleMapCatalog.WeaponPickupDefinition
  val WeaponPickupDefinition: BattleMapCatalog.WeaponPickupDefinition.type = BattleMapCatalog.WeaponPickupDefinition

  type ItemPickupDefinition = BattleMapCatalog.ItemPickupDefinition
  val ItemPickupDefinition: BattleMapCatalog.ItemPickupDefinition.type = BattleMapCatalog.ItemPickupDefinition

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
    recoilStrength: Double,
    usesHeat: Boolean = false,
    maxHeat: Double = 0.0,
    heatPerShot: Double = 0.0,
    coolRatePerSecond: Double = 0.0,
    overheatLockMs: Long = 0L
  )

  final case class SkillDefinition(
    skillKind: String,
    activationKind: String,
    effectType: String,
    cooldownMs: Long,
    activeMs: Long,
    range: Option[Double] = None,
    radius: Option[Double] = None,
    durationMs: Option[Long] = None,
    distance: Option[Double] = None,
    speedMultiplier: Option[Double] = None
  )

  val DefaultBattleDurationMs: Long = BattleRules.BattleDurationMs

  val spawnPoints: Vector[BattleVector2] = BattleMapCatalog.defaultMap.heroSpawnPoints

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
      magazineSize = 0,
      reserveAmmo = 0,
      pickupAmmo = 0,
      recoilStrength = 8.0,
      usesHeat = true,
      maxHeat = 100.0,
      heatPerShot = 8.0,
      coolRatePerSecond = 32.0,
      overheatLockMs = 1400L
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

  val medkitPickupKind: String = "Medkit"
  val medkitPickupRadius: Double = 40.0
  val medkitHealAmount: Int = 25
  val medkitRespawnMs: Long = 10000L
  val medkitPickupDefinitions: Vector[ItemPickupDefinition] = BattleMapCatalog.defaultMap.itemPickupDefinitions

  val weaponPickupKind: String = "Weapon"
  val weaponPickupRadius: Double = 40.0
  val weaponPickupRespawnMs: Long = 10000L
  val weaponPickupDefinitions: Vector[WeaponPickupDefinition] = BattleMapCatalog.defaultMap.weaponPickupDefinitions

  val blinkSkillKind: String = "Blink"
  val dashSkillKind: String = "Dash"
  val freezeSkillKind: String = "Freeze"

  val skillDefinitions: Map[String, SkillDefinition] = Map(
    blinkSkillKind -> SkillDefinition(
      skillKind = blinkSkillKind,
      activationKind = "prepared-target",
      effectType = "teleport",
      cooldownMs = 2200L,
      activeMs = 240L,
      range = Some(250.0)
    ),
    dashSkillKind -> SkillDefinition(
      skillKind = dashSkillKind,
      activationKind = "instant",
      effectType = "dash",
      cooldownMs = 5000L,
      activeMs = 180L,
      distance = Some(180.0)
    ),
    freezeSkillKind -> SkillDefinition(
      skillKind = freezeSkillKind,
      activationKind = "prepared-target",
      effectType = "slow-field",
      cooldownMs = 12000L,
      activeMs = 10000L,
      range = Some(520.0),
      radius = Some(150.0),
      durationMs = Some(10000L),
      speedMultiplier = Some(0.5)
    )
  )

  private def getSkillDefinition(kind: String): SkillDefinition =
    skillDefinitions.getOrElse(kind, throw new IllegalStateException(s"Missing skill definition for $kind"))

  private def requireSkillDouble(kind: String, fieldName: String, value: Option[Double]): Double =
    value.getOrElse(throw new IllegalStateException(s"Missing $fieldName for $kind skill definition"))

  private def requireSkillLong(kind: String, fieldName: String, value: Option[Long]): Long =
    value.getOrElse(throw new IllegalStateException(s"Missing $fieldName for $kind skill definition"))

  private val dashSkillDefinition: SkillDefinition = getSkillDefinition(dashSkillKind)
  val dashDistance: Double = requireSkillDouble(dashSkillKind, "distance", dashSkillDefinition.distance)
  val dashCooldownMs: Long = dashSkillDefinition.cooldownMs
  val dashActiveMs: Long = dashSkillDefinition.activeMs

  private val blinkSkillDefinition: SkillDefinition = getSkillDefinition(blinkSkillKind)
  val blinkRange: Double = requireSkillDouble(blinkSkillKind, "range", blinkSkillDefinition.range)
  val blinkCooldownMs: Long = blinkSkillDefinition.cooldownMs
  val blinkActiveMs: Long = blinkSkillDefinition.activeMs

  private val freezeSkillDefinition: SkillDefinition = getSkillDefinition(freezeSkillKind)
  val freezeRange: Double = requireSkillDouble(freezeSkillKind, "range", freezeSkillDefinition.range)
  val freezeRadius: Double = requireSkillDouble(freezeSkillKind, "radius", freezeSkillDefinition.radius)
  val freezeDurationMs: Long = requireSkillLong(freezeSkillKind, "durationMs", freezeSkillDefinition.durationMs)
  val freezeCooldownMs: Long = freezeSkillDefinition.cooldownMs
  val freezeSpeedMultiplier: Double =
    requireSkillDouble(freezeSkillKind, "speedMultiplier", freezeSkillDefinition.speedMultiplier)

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
