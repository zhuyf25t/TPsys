package services.battle.microservices.combat.objects.combat

import services.battle.microservices.combat.objects.projectile.ProjectileKind
import services.battle.microservices.combat.objects.weapon.{BattleWeaponHeat, BattleWeaponHeatRatePerSecond, WeaponKind}
import services.battle.objects.core.{
  CooldownMillis,
  DurationMillis,
  FacingRadians,
  Radius
}

private[services] enum BattleWeaponFiringResource {
  case Magazine
  case Heat
}

private[services] object BattleWeaponFiringResource {
  def wireValue(resource: BattleWeaponFiringResource): String =
    resource match {
      case BattleWeaponFiringResource.Magazine => "magazine"
      case BattleWeaponFiringResource.Heat     => "heat"
    }

  def fromWire(value: String): Option[BattleWeaponFiringResource] =
    value match {
      case "magazine" => Some(BattleWeaponFiringResource.Magazine)
      case "heat"     => Some(BattleWeaponFiringResource.Heat)
      case _          => None
    }
}

private[services] final case class BattleWeaponInventoryDefinition(
  weaponKind: WeaponKind,
  magazineSize: Int,
  reserveAmmo: Option[Int],
  pickupAmmo: Int,
  reloadMs: Int,
  firingResource: BattleWeaponFiringResource
) {
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

private[services] final case class BattleWeaponRuleDefinition(
  inventory: BattleWeaponInventoryDefinition,
  fire: BattleWeaponFireDefinition
) {
  def weaponKind: WeaponKind =
    inventory.weaponKind
}
