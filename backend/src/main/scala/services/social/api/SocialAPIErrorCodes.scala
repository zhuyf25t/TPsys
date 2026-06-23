package services.social.api

import services.social.services.{FriendRequestCreateError, FriendRequestRespondError}

enum SocialApiRequestDecodeError {
  case InvalidJsonObject
}

enum SocialApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case InvalidHandles
  case RequestNotFound
  case Forbidden
  case InvalidDecision
  case MissingFields
  case InvalidActor
}

object SocialApiErrorCode {
  def fromOwnerError(error: SocialRouteHandleError): SocialApiErrorCode =
    error match {
      case SocialRouteHandleError.Missing           => SocialApiErrorCode.MissingOwner
      case SocialRouteHandleError.VisitorNotAllowed => SocialApiErrorCode.VisitorNotAllowed
      case SocialRouteHandleError.Invalid           => SocialApiErrorCode.InvalidOwner
    }

  def fromCreateRouteError(error: SocialRouteCreateError): SocialApiErrorCode =
    error match {
      case SocialRouteCreateError.InvalidHandles    => SocialApiErrorCode.InvalidHandles
      case SocialRouteCreateError.VisitorNotAllowed => SocialApiErrorCode.VisitorNotAllowed
    }

  def fromCreateServiceError(error: FriendRequestCreateError): SocialApiErrorCode =
    error match {
      case FriendRequestCreateError.InvalidHandles => SocialApiErrorCode.InvalidHandles
    }

  def fromRespondRouteError(error: SocialRouteRespondError): SocialApiErrorCode =
    error match {
      case SocialRouteRespondError.InvalidDecision    => SocialApiErrorCode.InvalidDecision
      case SocialRouteRespondError.MissingFields      => SocialApiErrorCode.MissingFields
      case SocialRouteRespondError.InvalidActorHandle => SocialApiErrorCode.InvalidActor
      case SocialRouteRespondError.VisitorNotAllowed  => SocialApiErrorCode.VisitorNotAllowed
    }

  def fromRespondServiceError(error: FriendRequestRespondError): SocialApiErrorCode =
    error match {
      case FriendRequestRespondError.RequestNotFound => SocialApiErrorCode.RequestNotFound
      case FriendRequestRespondError.Forbidden       => SocialApiErrorCode.Forbidden
    }

  def wireValue(code: SocialApiErrorCode): String =
    code match {
      case SocialApiErrorCode.MethodNotAllowed  => "method_not_allowed"
      case SocialApiErrorCode.InvalidJsonObject => "bad_request"
      case SocialApiErrorCode.MissingOwner      => "missing_owner"
      case SocialApiErrorCode.VisitorNotAllowed => "visitor_not_allowed"
      case SocialApiErrorCode.InvalidOwner      => "invalid_owner"
      case SocialApiErrorCode.InvalidHandles    => "invalid_handles"
      case SocialApiErrorCode.RequestNotFound   => "request_not_found"
      case SocialApiErrorCode.Forbidden         => "forbidden"
      case SocialApiErrorCode.InvalidDecision   => "invalid_decision"
      case SocialApiErrorCode.MissingFields     => "missing_fields"
      case SocialApiErrorCode.InvalidActor      => "invalid_actor"
    }

  def message(code: SocialApiErrorCode): String =
    code match {
      case SocialApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case SocialApiErrorCode.InvalidJsonObject => "Request body must be a JSON object with string fields."
      case _                                    => wireValue(code)
    }

  def statusCode(code: SocialApiErrorCode): Int =
    code match {
      case SocialApiErrorCode.MethodNotAllowed  => 405
      case SocialApiErrorCode.VisitorNotAllowed => 403
      case SocialApiErrorCode.RequestNotFound   => 404
      case SocialApiErrorCode.Forbidden         => 403
      case _                                    => 400
    }
}
