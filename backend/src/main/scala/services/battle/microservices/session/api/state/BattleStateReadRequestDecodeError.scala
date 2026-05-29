package services.battle.microservices.session.api.state

enum BattleStateReadRequestDecodeError {
  case InvalidJsonObject
}

object BattleStateReadRequestDecodeError {
  def message(error: BattleStateReadRequestDecodeError): String =
    error match {
      case BattleStateReadRequestDecodeError.InvalidJsonObject =>
        "battleId is required."
    }
}
