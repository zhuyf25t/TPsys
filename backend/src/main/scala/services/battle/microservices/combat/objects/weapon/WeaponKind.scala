package services.battle.microservices.combat.objects.weapon

enum WeaponKind {
  case Pistol
  case RocketLauncher
  case Gatling
  case Shotgun
}

object WeaponKind {
  def wireValue(value: WeaponKind): String =
    value match {
      case WeaponKind.Pistol         => "Pistol"
      case WeaponKind.RocketLauncher => "RocketLauncher"
      case WeaponKind.Gatling        => "Gatling"
      case WeaponKind.Shotgun        => "Shotgun"
    }

  def fromWire(value: String): Option[WeaponKind] =
    value match {
      case "Pistol"         => Some(WeaponKind.Pistol)
      case "RocketLauncher" => Some(WeaponKind.RocketLauncher)
      case "Gatling"        => Some(WeaponKind.Gatling)
      case "Shotgun"        => Some(WeaponKind.Shotgun)
      case _                => None
    }
}
