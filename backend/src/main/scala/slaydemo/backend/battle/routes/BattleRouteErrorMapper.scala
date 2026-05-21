package slaydemo.backend.battle.routes

import slaydemo.backend.battle.services.{BattleRoomError, BattleStateReadError}

private[routes] final case class BattleRouteError(
  status: Int,
  code: String,
  message: String
)

private[routes] object BattleRouteErrorMapper {
  def badJsonObject(message: String): BattleRouteError =
    BattleRouteError(400, "bad_request", message)

  def unsupportedPost: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only POST and OPTIONS are supported.")

  def unsupportedGet: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET and OPTIONS are supported.")

  def unsupportedRooms: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET, POST, and OPTIONS are supported.")

  def unsupportedState: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET, HEAD, and OPTIONS are supported.")

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
