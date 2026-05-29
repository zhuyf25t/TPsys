package services.battle.microservices.runtime.objects.event

enum BattleEventKind {
  case Kill
  case Heal
  case Pickup
  case Respawn
}

object BattleEventKind {
  def wireValue(value: BattleEventKind): String =
    value match {
      case BattleEventKind.Kill    => "kill"
      case BattleEventKind.Heal    => "heal"
      case BattleEventKind.Pickup  => "pickup"
      case BattleEventKind.Respawn => "respawn"
    }

  def fromWire(value: String): Option[BattleEventKind] =
    value match {
      case "kill"    => Some(BattleEventKind.Kill)
      case "heal"    => Some(BattleEventKind.Heal)
      case "pickup"  => Some(BattleEventKind.Pickup)
      case "respawn" => Some(BattleEventKind.Respawn)
      case _         => None
    }
}
