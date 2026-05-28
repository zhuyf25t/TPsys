package services.governance.api

import io.circe.Error

import system.api.APIMessageError

object GovernanceAPIMessageSupport {
  def invalidJsonObject(error: Error): APIMessageError =
    GovernanceAPIMessageSupport.error(GovernanceApiErrorCode.InvalidJsonObject)

  def error(code: GovernanceApiErrorCode): APIMessageError =
    code match {
      case GovernanceApiErrorCode.InvalidActor =>
        APIMessageError.Forbidden(GovernanceApiErrorCode.message(code))
      case _ =>
        APIMessageError.BadRequest(GovernanceApiErrorCode.message(code))
    }
}
