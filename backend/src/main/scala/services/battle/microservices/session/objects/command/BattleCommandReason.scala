package services.battle.microservices.session.objects.command

enum BattleCommandReason {
  case BattleFinished
  case BattleInactive
  case PlayerDead
}

object BattleCommandReason {
  def wireValue(value: BattleCommandReason): String =
    value match {
      case BattleCommandReason.BattleFinished => "battle_finished"
      case BattleCommandReason.BattleInactive => "battle_inactive"
      case BattleCommandReason.PlayerDead     => "player_dead"
    }

  def fromWire(value: String): Option[BattleCommandReason] =
    value match {
      case "battle_finished" => Some(BattleCommandReason.BattleFinished)
      case "battle_inactive" => Some(BattleCommandReason.BattleInactive)
      case "player_dead"     => Some(BattleCommandReason.PlayerDead)
      case _                 => None
    }
}
