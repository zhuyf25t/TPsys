package services.battle.microservices.runtime.objects.command

enum BattleCommandReason {
  case BattleFinished
  case BattleInactive
  case PlayerDead
  case StaleCommand
}

object BattleCommandReason {
  def wireValue(value: BattleCommandReason): String =
    value match {
      case BattleCommandReason.BattleFinished => "battle_finished"
      case BattleCommandReason.BattleInactive => "battle_inactive"
      case BattleCommandReason.PlayerDead     => "player_dead"
      case BattleCommandReason.StaleCommand   => "stale_command"
    }

  def fromWire(value: String): Option[BattleCommandReason] =
    value match {
      case "battle_finished" => Some(BattleCommandReason.BattleFinished)
      case "battle_inactive" => Some(BattleCommandReason.BattleInactive)
      case "player_dead"     => Some(BattleCommandReason.PlayerDead)
      case "stale_command"   => Some(BattleCommandReason.StaleCommand)
      case _                 => None
    }
}
