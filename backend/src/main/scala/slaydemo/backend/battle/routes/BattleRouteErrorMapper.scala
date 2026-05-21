package slaydemo.backend.battle.routes

import slaydemo.backend.battle.services.BattleStateReadError

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

  def unsupportedState: BattleRouteError =
    BattleRouteError(405, "method_not_allowed", "Only GET, HEAD, and OPTIONS are supported.")

  def invalidBattleId: BattleRouteError =
    BattleRouteError(400, "invalid_battle_id", "battleId is required.")

  def stateRead(error: BattleStateReadError): BattleRouteError =
    error match {
      case BattleStateReadError.BattleNotFound =>
        BattleRouteError(404, "battle_not_found", "battle_not_found")
    }

}
