package services.social.api

import io.circe.Error

import system.api.APIMessageError

object SocialAPIMessageSupport {
  def error(code: SocialApiErrorCode): APIMessageError =
    code match {
      case SocialApiErrorCode.VisitorNotAllowed | SocialApiErrorCode.Forbidden =>
        APIMessageError.Forbidden(SocialApiErrorCode.message(code))
      case SocialApiErrorCode.RequestNotFound =>
        APIMessageError.NotFound(SocialApiErrorCode.message(code))
      case _ =>
        APIMessageError.BadRequest(SocialApiErrorCode.message(code))
    }

  def invalidJsonObject(error: Error): APIMessageError =
    APIMessageError.BadRequest(SocialApiErrorCode.message(SocialApiErrorCode.InvalidJsonObject))
}
