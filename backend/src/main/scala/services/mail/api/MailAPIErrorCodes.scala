package services.mail.api

enum MailReadApiRequestDecodeError {
  case InvalidJsonObject
}

enum MailApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case MissingMailId
  case MailNotFound
}

object MailApiErrorCode {
  def fromOwnerError(error: MailRouteOwnerError): MailApiErrorCode =
    error match {
      case MailRouteOwnerError.MissingOwner      => MailApiErrorCode.MissingOwner
      case MailRouteOwnerError.VisitorNotAllowed => MailApiErrorCode.VisitorNotAllowed
      case MailRouteOwnerError.InvalidOwner      => MailApiErrorCode.InvalidOwner
    }

  def fromReadError(error: MailRouteReadError): MailApiErrorCode =
    error match {
      case MailRouteReadError.MissingOwner      => MailApiErrorCode.MissingOwner
      case MailRouteReadError.VisitorNotAllowed => MailApiErrorCode.VisitorNotAllowed
      case MailRouteReadError.InvalidOwner      => MailApiErrorCode.InvalidOwner
      case MailRouteReadError.MissingMailId     => MailApiErrorCode.MissingMailId
    }

  def wireValue(code: MailApiErrorCode): String =
    code match {
      case MailApiErrorCode.MethodNotAllowed  => "method_not_allowed"
      case MailApiErrorCode.InvalidJsonObject => "bad_request"
      case MailApiErrorCode.MissingOwner      => "missing_owner"
      case MailApiErrorCode.VisitorNotAllowed => "visitor_not_allowed"
      case MailApiErrorCode.InvalidOwner      => "invalid_owner"
      case MailApiErrorCode.MissingMailId     => "missing_mail_id"
      case MailApiErrorCode.MailNotFound      => "mail_not_found"
    }

  def message(code: MailApiErrorCode): String =
    code match {
      case MailApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case MailApiErrorCode.InvalidJsonObject => "Request body must be a JSON object with string fields."
      case _                                  => wireValue(code)
    }

  def statusCode(code: MailApiErrorCode): Int =
    code match {
      case MailApiErrorCode.MethodNotAllowed  => 405
      case MailApiErrorCode.VisitorNotAllowed => 403
      case MailApiErrorCode.MailNotFound      => 404
      case _                                  => 400
    }
}
