package services.identity.api

import services.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationError,
  IdentitySessionError
}

enum IdentityApiRequestDecodeError {
  case InvalidJsonObject
}

enum IdentityApiErrorCode {
  case PostMethodNotAllowed
  case GetMethodNotAllowed
  case InvalidJsonObject
  case InvalidHandle
  case InvalidPassword
  case InvalidSkin
  case HandleTaken
  case InvalidCredentials
  case MissingSession
  case InvalidSession
}

object IdentityApiErrorCode {
  def fromRegistrationParseError(error: IdentityRegistrationCommandParseError): IdentityApiErrorCode =
    error match {
      case IdentityRegistrationCommandParseError.InvalidHandle   => IdentityApiErrorCode.InvalidHandle
      case IdentityRegistrationCommandParseError.InvalidPassword => IdentityApiErrorCode.InvalidPassword
      case IdentityRegistrationCommandParseError.InvalidSkin     => IdentityApiErrorCode.InvalidSkin
    }

  def fromRegistrationServiceError(error: IdentityRegistrationError): IdentityApiErrorCode =
    error match {
      case IdentityRegistrationError.HandleTaken => IdentityApiErrorCode.HandleTaken
    }

  def fromSessionParseError(error: IdentitySessionCommandParseError): IdentityApiErrorCode =
    error match {
      case IdentitySessionCommandParseError.InvalidCredentials => IdentityApiErrorCode.InvalidCredentials
    }

  def fromSessionServiceError(error: IdentitySessionError): IdentityApiErrorCode =
    error match {
      case IdentitySessionError.InvalidCredentials => IdentityApiErrorCode.InvalidCredentials
    }

  def fromCurrentSessionError(error: IdentityCurrentSessionError): IdentityApiErrorCode =
    error match {
      case IdentityCurrentSessionError.MissingSession => IdentityApiErrorCode.MissingSession
      case IdentityCurrentSessionError.InvalidSession => IdentityApiErrorCode.InvalidSession
    }

  def wireValue(code: IdentityApiErrorCode): String =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => "method_not_allowed"
      case IdentityApiErrorCode.GetMethodNotAllowed  => "method_not_allowed"
      case IdentityApiErrorCode.InvalidJsonObject    => "bad_request"
      case IdentityApiErrorCode.InvalidHandle        => "invalid_handle"
      case IdentityApiErrorCode.InvalidPassword      => "invalid_password"
      case IdentityApiErrorCode.InvalidSkin          => "invalid_skin"
      case IdentityApiErrorCode.HandleTaken          => "handle_taken"
      case IdentityApiErrorCode.InvalidCredentials   => "invalid_credentials"
      case IdentityApiErrorCode.MissingSession       => "missing_session"
      case IdentityApiErrorCode.InvalidSession       => "invalid_session"
    }

  def message(code: IdentityApiErrorCode): String =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => "Only POST and OPTIONS are supported."
      case IdentityApiErrorCode.GetMethodNotAllowed  => "Only GET and OPTIONS are supported."
      case IdentityApiErrorCode.InvalidJsonObject    => "Request body must be a JSON object with string fields."
      case IdentityApiErrorCode.InvalidHandle        => "Handle must be 3-16 characters and use letters, numbers, -, _."
      case IdentityApiErrorCode.InvalidPassword      => "Password must be at least 4 characters."
      case IdentityApiErrorCode.InvalidSkin          => "Skin must be one of: blue, old, soldier, survivor, zombie."
      case IdentityApiErrorCode.HandleTaken          => "Handle already exists."
      case IdentityApiErrorCode.InvalidCredentials   => "Handle or password is incorrect."
      case IdentityApiErrorCode.MissingSession       => "Session token is required."
      case IdentityApiErrorCode.InvalidSession       => "Current session is not valid."
    }

  def statusCode(code: IdentityApiErrorCode): Int =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => 405
      case IdentityApiErrorCode.GetMethodNotAllowed  => 405
      case IdentityApiErrorCode.HandleTaken          => 409
      case IdentityApiErrorCode.InvalidCredentials   => 401
      case IdentityApiErrorCode.MissingSession       => 401
      case IdentityApiErrorCode.InvalidSession       => 401
      case _                                        => 400
    }
}
