package services.battle.microservices.combat.objects.projectile

enum ProjectileTerminalReason {
  case Hit
  case Expired
  case Blocked
  case OutOfBounds
}

object ProjectileTerminalReason {
  def wireValue(value: ProjectileTerminalReason): String =
    value match {
      case ProjectileTerminalReason.Hit         => "hit"
      case ProjectileTerminalReason.Expired     => "ttl"
      case ProjectileTerminalReason.Blocked     => "obstacle"
      case ProjectileTerminalReason.OutOfBounds => "world"
    }

  def fromWire(value: String): Option[ProjectileTerminalReason] =
    value match {
      case "hit"      => Some(ProjectileTerminalReason.Hit)
      case "ttl"      => Some(ProjectileTerminalReason.Expired)
      case "obstacle" => Some(ProjectileTerminalReason.Blocked)
      case "world"    => Some(ProjectileTerminalReason.OutOfBounds)
      case _          => None
    }
}
