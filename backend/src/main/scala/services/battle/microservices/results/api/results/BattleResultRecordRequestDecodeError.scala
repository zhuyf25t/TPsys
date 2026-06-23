package services.battle.microservices.results.api.results

enum BattleResultRecordRequestDecodeError {
  case BadJson
  case InvalidField(field: String)
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

object BattleResultRecordRequestDecodeError {
  def message(error: BattleResultRecordRequestDecodeError): String =
    error match {
      case BattleResultRecordRequestDecodeError.BadJson =>
        "Request body must be a JSON object."
      case BattleResultRecordRequestDecodeError.InvalidField(field) =>
        s"invalid_field_$field"
      case BattleResultRecordRequestDecodeError.InvalidBattleId =>
        "invalid_battle_id"
      case BattleResultRecordRequestDecodeError.InvalidHandle =>
        "invalid_handle"
      case BattleResultRecordRequestDecodeError.VisitorNotAllowed =>
        "visitor_not_allowed"
    }
}
