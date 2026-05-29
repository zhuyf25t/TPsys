package services.battle.microservices.session.api.command

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

enum BattleCommandRequestDecodeError {
  case InvalidJsonObject
  case MissingTicket
  case InvalidField(field: BattleCommandRequestField)
}

object BattleCommandRequestDecodeError {
  def message(error: BattleCommandRequestDecodeError): String =
    error match {
      case BattleCommandRequestDecodeError.InvalidJsonObject =>
        "invalid_json_object"
      case BattleCommandRequestDecodeError.MissingTicket =>
        "missing_ticket"
      case BattleCommandRequestDecodeError.InvalidField(field) =>
        field.toString
    }
}
