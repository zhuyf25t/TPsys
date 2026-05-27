package services.battle.database.combat

import services.battle.objects.{ProjectileKind, WeaponKind}
import services.battle.objects.core.{
  BattleWeaponHeat,
  BattleWeaponHeatRatePerSecond,
  CooldownMillis,
  Damage,
  DurationMillis,
  FacingRadians,
  Radius
}

private[services] enum BattleWeaponFiringResource {
  case Magazine
  case Heat
}

private[services] final case class BattleWeaponInventoryDefinition(
  weaponKind: WeaponKind,
  magazineSize: Int,
  reserveAmmo: Option[Int],
  pickupAmmo: Int,
  reloadMs: Int,
  firingResource: BattleWeaponFiringResource
) {
  /** 中文名：usesheatresource（usesHeatResource）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def usesHeatResource: Boolean =
    firingResource == BattleWeaponFiringResource.Heat
}

private[services] final case class BattleWeaponProjectileSpeed(value: Double) extends AnyVal
private[services] final case class BattleWeaponRecoilStrength(value: Double) extends AnyVal
private[services] final case class BattleWeaponProjectileCount(value: Int) extends AnyVal

private[services] final case class BattleWeaponProjectileDefinition(
  projectileKind: ProjectileKind,
  speed: BattleWeaponProjectileSpeed,
  damage: Damage,
  radius: Radius,
  lifetime: DurationMillis,
  splashRadius: Radius,
  projectileCount: BattleWeaponProjectileCount,
  spread: FacingRadians
)

private[services] final case class BattleWeaponHeatDefinition(
  maxHeat: BattleWeaponHeat,
  heatPerShot: BattleWeaponHeat,
  coolRatePerSecond: BattleWeaponHeatRatePerSecond,
  overheatLockMs: CooldownMillis
)

private[services] final case class BattleWeaponFireDefinition(
  weaponKind: WeaponKind,
  cooldownMs: CooldownMillis,
  projectile: BattleWeaponProjectileDefinition,
  recoilStrength: BattleWeaponRecoilStrength,
  heat: Option[BattleWeaponHeatDefinition]
)

private[services] object BattleWeaponCatalog {
  /** 中文名：inventorydefinition（inventoryDefinition）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def inventoryDefinition(weaponKind: WeaponKind): BattleWeaponInventoryDefinition =
    weaponKind match {
      case WeaponKind.Pistol =>
        BattleWeaponInventoryDefinition(
          weaponKind = WeaponKind.Pistol,
          magazineSize = 12,
          reserveAmmo = Some(48),
          pickupAmmo = 24,
          reloadMs = 1000,
          firingResource = BattleWeaponFiringResource.Magazine
        )
      case WeaponKind.RocketLauncher =>
        BattleWeaponInventoryDefinition(
          weaponKind = WeaponKind.RocketLauncher,
          magazineSize = 1,
          reserveAmmo = Some(3),
          pickupAmmo = 1,
          reloadMs = 2500,
          firingResource = BattleWeaponFiringResource.Magazine
        )
      case WeaponKind.Gatling =>
        BattleWeaponInventoryDefinition(
          weaponKind = WeaponKind.Gatling,
          magazineSize = 0,
          reserveAmmo = Some(0),
          pickupAmmo = 0,
          reloadMs = 0,
          firingResource = BattleWeaponFiringResource.Heat
        )
      case WeaponKind.Shotgun =>
        BattleWeaponInventoryDefinition(
          weaponKind = WeaponKind.Shotgun,
          magazineSize = 6,
          reserveAmmo = Some(18),
          pickupAmmo = 6,
          reloadMs = 1200,
          firingResource = BattleWeaponFiringResource.Magazine
        )
    }

  /** 中文名：开火definition（fireDefinition）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def fireDefinition(weaponKind: WeaponKind): BattleWeaponFireDefinition =
    weaponKind match {
      case WeaponKind.Pistol =>
        BattleWeaponFireDefinition(
          weaponKind = WeaponKind.Pistol,
          cooldownMs = CooldownMillis(260),
          projectile = BattleWeaponProjectileDefinition(
            projectileKind = ProjectileKind.PistolBullet,
            speed = BattleWeaponProjectileSpeed(1400.0),
            damage = Damage(12),
            radius = Radius(8.0),
            lifetime = DurationMillis(30000L),
            splashRadius = Radius(0.0),
            projectileCount = BattleWeaponProjectileCount(1),
            spread = FacingRadians(0.0)
          ),
          recoilStrength = BattleWeaponRecoilStrength(20.0),
          heat = None
        )
      case WeaponKind.RocketLauncher =>
        BattleWeaponFireDefinition(
          weaponKind = WeaponKind.RocketLauncher,
          cooldownMs = CooldownMillis(160),
          projectile = BattleWeaponProjectileDefinition(
            projectileKind = ProjectileKind.Rocket,
            speed = BattleWeaponProjectileSpeed(340.0),
            damage = Damage(60),
            radius = Radius(14.0),
            lifetime = DurationMillis(30000L),
            splashRadius = Radius(132.0),
            projectileCount = BattleWeaponProjectileCount(1),
            spread = FacingRadians(0.0)
          ),
          recoilStrength = BattleWeaponRecoilStrength(120.0),
          heat = None
        )
      case WeaponKind.Gatling =>
        BattleWeaponFireDefinition(
          weaponKind = WeaponKind.Gatling,
          cooldownMs = CooldownMillis(72),
          projectile = BattleWeaponProjectileDefinition(
            projectileKind = ProjectileKind.GatlingBullet,
            speed = BattleWeaponProjectileSpeed(980.0),
            damage = Damage(5),
            radius = Radius(7.0),
            lifetime = DurationMillis(30000L),
            splashRadius = Radius(0.0),
            projectileCount = BattleWeaponProjectileCount(1),
            spread = FacingRadians(0.06)
          ),
          recoilStrength = BattleWeaponRecoilStrength(8.0),
          heat = Some(
            BattleWeaponHeatDefinition(
              maxHeat = BattleWeaponHeat(100),
              heatPerShot = BattleWeaponHeat(8),
              coolRatePerSecond = BattleWeaponHeatRatePerSecond(32),
              overheatLockMs = CooldownMillis(1400)
            )
          )
        )
      case WeaponKind.Shotgun =>
        BattleWeaponFireDefinition(
          weaponKind = WeaponKind.Shotgun,
          cooldownMs = CooldownMillis(760),
          projectile = BattleWeaponProjectileDefinition(
            projectileKind = ProjectileKind.ShotgunPellet,
            speed = BattleWeaponProjectileSpeed(720.0),
            damage = Damage(8),
            radius = Radius(7.0),
            lifetime = DurationMillis(30000L),
            splashRadius = Radius(0.0),
            projectileCount = BattleWeaponProjectileCount(5),
            spread = FacingRadians(0.42)
          ),
          recoilStrength = BattleWeaponRecoilStrength(80.0),
          heat = None
        )
    }

  /** 中文名：换弹ms（reloadMs）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def reloadMs(weaponKind: WeaponKind): Int =
    inventoryDefinition(weaponKind).reloadMs

  /** 中文名：usesheatresource（usesHeatResource）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def usesHeatResource(weaponKind: WeaponKind): Boolean =
    inventoryDefinition(weaponKind).usesHeatResource

  /** 中文名：heatdefinition（heatDefinition）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def heatDefinition(weaponKind: WeaponKind): Option[BattleWeaponHeatDefinition] =
    fireDefinition(weaponKind).heat
}
