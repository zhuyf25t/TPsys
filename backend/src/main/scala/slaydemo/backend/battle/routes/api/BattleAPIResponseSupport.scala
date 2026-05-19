package slaydemo.backend.battle.routes

import slaydemo.backend.shared.api.BackendAPIResponse

private[routes] object BattleAPIResponseSupport {
  def error(error: BattleRouteError): BackendAPIResponse =
    BackendAPIResponse.jsonError(error.status, error.code, error.message)

  def badJsonObject(message: String): BackendAPIResponse =
    error(BattleRouteErrorMapper.badJsonObject(message))

  def unsupportedPost: BackendAPIResponse =
    error(BattleRouteErrorMapper.unsupportedPost)

  def unsupportedGet: BackendAPIResponse =
    error(BattleRouteErrorMapper.unsupportedGet)

  def unsupportedState: BackendAPIResponse =
    error(BattleRouteErrorMapper.unsupportedState)
}
