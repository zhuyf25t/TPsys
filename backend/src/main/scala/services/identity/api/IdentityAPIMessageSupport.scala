package services.identity.api

import io.circe.Error

import system.api.APIMessageError

object IdentityAPIMessageSupport {
  def error(code: IdentityApiErrorCode): APIMessageError =
    code match {
      case IdentityApiErrorCode.HandleTaken =>
        APIMessageError.Conflict(IdentityApiErrorCode.message(code))
      case IdentityApiErrorCode.InvalidCredentials | IdentityApiErrorCode.MissingSession | IdentityApiErrorCode.InvalidSession =>
        APIMessageError.Unauthorized(IdentityApiErrorCode.message(code))
      case _ =>
        APIMessageError.BadRequest(IdentityApiErrorCode.message(code))
    }

  def invalidJsonObject(error: Error): APIMessageError =
    APIMessageError.BadRequest(IdentityApiErrorCode.message(IdentityApiErrorCode.InvalidJsonObject))
}
