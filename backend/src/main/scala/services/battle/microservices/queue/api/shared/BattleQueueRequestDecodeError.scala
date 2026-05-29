package services.battle.microservices.queue.api.shared

enum BattleQueueRequestDecodeError {
  case InvalidJsonObject
  case InvalidRating
  case InvalidHandle
  case InvalidBattleMode
  case MissingSession
  case MissingTicketId
  case MissingRoomId
}

object BattleQueueRequestDecodeError {
  def message(error: BattleQueueRequestDecodeError): String =
    error match {
      case BattleQueueRequestDecodeError.InvalidJsonObject =>
        "invalid_json_object"
      case BattleQueueRequestDecodeError.InvalidRating =>
        "invalid_rating"
      case BattleQueueRequestDecodeError.InvalidHandle =>
        "invalid_handle"
      case BattleQueueRequestDecodeError.InvalidBattleMode =>
        "invalid_battle_mode"
      case BattleQueueRequestDecodeError.MissingSession =>
        "missing_session"
      case BattleQueueRequestDecodeError.MissingTicketId =>
        "missing_ticket_id"
      case BattleQueueRequestDecodeError.MissingRoomId =>
        "missing_room_id"
    }
}
