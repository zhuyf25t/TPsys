package services.battle.microservices.combat.objects.projectile

enum ProjectileKind {
  case PistolBullet
  case Rocket
  case GatlingBullet
  case ShotgunPellet
}

object ProjectileKind {
  def wireValue(value: ProjectileKind): String =
    value match {
      case ProjectileKind.PistolBullet  => "pistol-bullet"
      case ProjectileKind.Rocket        => "rocket"
      case ProjectileKind.GatlingBullet => "gatling-bullet"
      case ProjectileKind.ShotgunPellet => "shotgun-pellet"
    }

  def fromWire(value: String): Option[ProjectileKind] =
    value match {
      case "pistol-bullet"  => Some(ProjectileKind.PistolBullet)
      case "rocket"         => Some(ProjectileKind.Rocket)
      case "gatling-bullet" => Some(ProjectileKind.GatlingBullet)
      case "shotgun-pellet" => Some(ProjectileKind.ShotgunPellet)
      case _                => None
    }
}
