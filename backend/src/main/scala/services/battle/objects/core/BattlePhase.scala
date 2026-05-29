package services.battle.objects.core

enum BattlePhase {
  case Waiting
  case Active
  case Finished
}

object BattlePhase {
  def wireValue(value: BattlePhase): String =
    value match {
      case BattlePhase.Waiting  => "waiting"
      case BattlePhase.Active   => "active"
      case BattlePhase.Finished => "finished"
    }

  def fromWire(value: String): Option[BattlePhase] =
    value match {
      case "waiting"  => Some(BattlePhase.Waiting)
      case "active"   => Some(BattlePhase.Active)
      case "finished" => Some(BattlePhase.Finished)
      case _          => None
    }
}
