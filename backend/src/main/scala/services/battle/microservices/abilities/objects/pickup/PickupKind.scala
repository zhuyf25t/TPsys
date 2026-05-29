package services.battle.microservices.abilities.objects.pickup

enum PickupKind {
  case Medkit
  case Weapon
}

object PickupKind {
  def wireValue(value: PickupKind): String =
    value match {
      case PickupKind.Medkit => "Medkit"
      case PickupKind.Weapon => "Weapon"
    }

  def fromWire(value: String): Option[PickupKind] =
    value match {
      case "Medkit" => Some(PickupKind.Medkit)
      case "Weapon" => Some(PickupKind.Weapon)
      case _        => None
    }
}
