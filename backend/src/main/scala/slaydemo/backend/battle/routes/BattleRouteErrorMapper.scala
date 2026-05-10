package slaydemo.backend.battle.routes

import slaydemo.backend.battle.services.{
  BattleCommandSubmitError,
  BattleQueueJoinAuthorizationError,
  BattleQueueStatusError,
  BattleRoomError,
  BattleStateReadError
}

private[routes] final case class BattleRouteError(
  status: Int,
  code: String,
  message: String
)

private[routes] object BattleRouteErrorMapper {
  def badJsonObject(message: String): BattleRouteError =
    BattleRouteError(400, "bad_request", message)

  def joinCommandParse(message: String): BattleRouteError =
    BattleRouteError(400, "bad_request", message)

  def joinCommandParse(error: BattleQueueJoinCommandParseError): BattleRouteError =
    error match {
      case BattleQueueJoinCommandParseError.InvalidHandle =>
        BattleRouteError(400, "invalid_handle", "Handle must be a playable non-visitor handle.")
      case BattleQueueJoinCommandParseError.MissingSession =>
        BattleRouteError(401, "missing_session", "Session token is required.")
    }

  def joinAuthorization(error: BattleQueueJoinAuthorizationError): BattleRouteError =
    error match {
      case BattleQueueJoinAuthorizationError.InvalidSession =>
        BattleRouteError(401, "invalid_session", "Session token is not valid.")
      case BattleQueueJoinAuthorizationError.HandleMismatch =>
        BattleRouteError(403, "identity_mismatch", "Session does not belong to the requested handle.")
    }

  def missingTicketId: BattleRouteError =
    BattleRouteError(400, "missing_ticket_id", "ticketId query parameter is required.")

  def queueStatus(error: BattleQueueStatusError): BattleRouteError =
    error match {
      case BattleQueueStatusError.TicketNotFound =>
        BattleRouteError(404, "ticket_not_found", "Queue ticket was not found.")
    }

  def queueLeaveParse(message: String): BattleRouteError =
    BattleRouteError(400, "bad_request", message)

  def unsupportedPost: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only POST and OPTIONS are supported.")

  def unsupportedGet: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET and OPTIONS are supported.")

  def unsupportedRooms: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET, POST, and OPTIONS are supported.")

  def unsupportedState: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET, HEAD, and OPTIONS are supported.")

  def commandRequest(error: BattleCommandRequestParseError): BattleRouteError =
    error match {
      case BattleCommandRequestParseError.MissingTicket =>
        BattleRouteError(403, "command_not_authorized", "command_not_authorized")
      case BattleCommandRequestParseError.BadRequest(message) =>
        BattleRouteError(400, message, message)
    }

  def commandSubmit(error: BattleCommandSubmitError): BattleRouteError =
    error match {
      case BattleCommandSubmitError.BattleNotFound =>
        BattleRouteError(404, "battle_not_found", "battle_not_found")
      case BattleCommandSubmitError.PlayerNotFound =>
        BattleRouteError(400, "player_not_found", "player_not_found")
      case BattleCommandSubmitError.BotCommandsNotSupported =>
        BattleRouteError(400, "bot_commands_not_supported", "bot_commands_not_supported")
      case BattleCommandSubmitError.CommandNotAuthorized =>
        BattleRouteError(403, "command_not_authorized", "command_not_authorized")
    }

  def invalidBattleId: BattleRouteError =
    BattleRouteError(400, "invalid_battle_id", "battleId is required.")

  def stateRead(error: BattleStateReadError): BattleRouteError =
    error match {
      case BattleStateReadError.BattleNotFound =>
        BattleRouteError(404, "battle_not_found", "battle_not_found")
    }

  def roomSnapshotTarget(target: BattleRoomSnapshotTarget): Option[BattleRouteError] =
    target match {
      case BattleRoomSnapshotTarget.MissingRoomId =>
        Some(BattleRouteError(400, "invalid_room_id", "roomId is required."))
      case BattleRoomSnapshotTarget.RouteNotFound =>
        Some(BattleRouteError(404, "route_not_found", "Battle room route was not found."))
      case BattleRoomSnapshotTarget.Room(_) =>
        None
    }

  def roomRouteNotFound: BattleRouteError =
    BattleRouteError(404, "route_not_found", "Battle room route was not found.")

  def room(error: BattleRoomError): BattleRouteError =
    error match {
      case BattleRoomError.MissingRoomId =>
        BattleRouteError(400, "invalid_room_id", "roomId is required.")
      case BattleRoomError.RoomNotFound =>
        BattleRouteError(404, "room_not_found", "Battle room was not found.")
    }
}
