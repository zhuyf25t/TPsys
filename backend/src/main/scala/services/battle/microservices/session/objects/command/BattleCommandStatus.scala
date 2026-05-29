package services.battle.microservices.session.objects.command

enum BattleCommandStatus {
  case Applied
  case Ignored
}

object BattleCommandStatus {
  def wireValue(value: BattleCommandStatus): String =
    value match {
      case BattleCommandStatus.Applied => "applied"
      case BattleCommandStatus.Ignored => "ignored"
    }

  def fromWire(value: String): Option[BattleCommandStatus] =
    value match {
      case "applied" => Some(BattleCommandStatus.Applied)
      case "ignored" => Some(BattleCommandStatus.Ignored)
      case _         => None
    }
}
