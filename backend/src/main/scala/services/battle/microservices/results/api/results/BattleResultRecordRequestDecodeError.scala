package services.battle.microservices.results.api.results

enum BattleResultRecordRequestDecodeError {
  case BadJson
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

object BattleResultRecordRequestDecodeError {
  def message(error: BattleResultRecordRequestDecodeError): String =
    error match {
      case BattleResultRecordRequestDecodeError.BadJson =>
        "Request body must be a JSON object."
      case BattleResultRecordRequestDecodeError.InvalidBattleId =>
        "invalid_battle_id"
      case BattleResultRecordRequestDecodeError.InvalidHandle =>
        "invalid_handle"
      case BattleResultRecordRequestDecodeError.VisitorNotAllowed =>
        "visitor_not_allowed"
    }
}
