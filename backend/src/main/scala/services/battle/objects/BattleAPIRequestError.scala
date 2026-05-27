package services.battle.objects

enum BattleCommandRequestField {
  case BattleId
  case PlayerId
  case ClientTick
  case ClientCommandSeq
  case Movement
  case Aim
  case PrimaryHeld
  case Sprint
  case ReloadPressed
  case CastDash
  case CastBlink
  case CastFreeze
  case PointerWorld
  case SwitchWeaponDirection
  case SwitchWeaponIndex
}

object BattleCommandRequestField {
  def fromDecoderMessage(message: String): Option[BattleCommandRequestField] =
    BattleCommandRequestField.values.find(_.toString == message)
}

enum BattleAPIRequestError {
  case InvalidJsonObject
  case InvalidRating
  case InvalidHandle
  case InvalidBattleMode
  case MissingSession
  case MissingTicketId
  case MissingRoomId
  case BadJson
  case InvalidBattleId
  case VisitorNotAllowed
  case MissingTicket
  case InvalidField(field: BattleCommandRequestField)
}

object BattleAPIRequestError {
  def message(error: BattleAPIRequestError): String =
    error match {
      case BattleAPIRequestError.InvalidJsonObject =>
        "invalid_json_object"
      case BattleAPIRequestError.InvalidRating =>
        "invalid_rating"
      case BattleAPIRequestError.InvalidHandle =>
        "invalid_handle"
      case BattleAPIRequestError.InvalidBattleMode =>
        "invalid_battle_mode"
      case BattleAPIRequestError.MissingSession =>
        "missing_session"
      case BattleAPIRequestError.MissingTicketId =>
        "missing_ticket_id"
      case BattleAPIRequestError.MissingRoomId =>
        "missing_room_id"
      case BattleAPIRequestError.BadJson =>
        "Request body must be a JSON object."
      case BattleAPIRequestError.InvalidBattleId =>
        "invalid_battle_id"
      case BattleAPIRequestError.VisitorNotAllowed =>
        "visitor_not_allowed"
      case BattleAPIRequestError.MissingTicket =>
        "missing_ticket"
      case BattleAPIRequestError.InvalidField(field) =>
        field.toString
    }

  def stateReadMessage(error: BattleAPIRequestError): String =
    error match {
      case BattleAPIRequestError.InvalidJsonObject =>
        "battleId is required."
      case other =>
        message(other)
    }
}
