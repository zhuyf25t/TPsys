package services.mail.api

import io.circe.Error

import system.api.APIMessageError

object MailAPIMessageSupport {
  def error(code: MailApiErrorCode): APIMessageError =
    code match {
      case MailApiErrorCode.VisitorNotAllowed =>
        APIMessageError.Forbidden(MailApiErrorCode.message(code))
      case MailApiErrorCode.MailNotFound =>
        APIMessageError.NotFound(MailApiErrorCode.message(code))
      case _ =>
        APIMessageError.BadRequest(MailApiErrorCode.message(code))
    }

  def invalidJsonObject(error: Error): APIMessageError =
    APIMessageError.BadRequest(MailApiErrorCode.message(MailApiErrorCode.InvalidJsonObject))
}
