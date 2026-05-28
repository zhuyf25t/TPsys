package services.social.api

import services.identity.objects.PlayerHandle
import services.social.objects.{FriendRequestDecision, FriendRequestId}
import services.social.objects.apiTypes.{FriendRequestCreateApiRequest, FriendRequestRespondApiRequest}
import services.social.services.{FriendRequestCreateError, FriendRequestRespondError}
import system.policies.HandlePolicy

object SocialRequestTarget {
  private val FriendRequestPaths: Set[String] =
    Set("/social/friend-requests", "/api/social/friend-requests")
  private val FriendRequestRespondPaths: Set[String] =
    Set("/social/friend-requests/respond", "/api/social/friend-requests/respond")

  def isFriendRequestPath(path: String): Boolean =
    FriendRequestPaths.contains(path)

  def isFriendRequestRespondPath(path: String): Boolean =
    FriendRequestRespondPaths.contains(path)
}

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

object FriendRequestOwnerQuery {
  def parseFromQuery(query: Map[String, String]): Either[SocialRouteHandleError, PlayerHandle] =
    parse(query.get("ownerHandle"))

  def parse(ownerHandle: Option[String]): Either[SocialRouteHandleError, PlayerHandle] =
    SocialCommandParsers.parseOwner(ownerHandle)
}

object SocialCommandParsers {
  def parseOwner(ownerHandle: Option[String]): Either[SocialRouteHandleError, PlayerHandle] =
    parseOwnerHandle(ownerHandle)

  def parseCreateHandles(request: FriendRequestCreateApiRequest): Either[SocialRouteCreateError, SocialCreateHandles] =
    parseCreateHandle(request.sourceHandle) match {
      case Left(error) =>
        Left(error)
      case Right(source) =>
        parseCreateHandle(request.targetHandle).map(target => SocialCreateHandles(source, target))
    }

  def parseRespondCommand(request: FriendRequestRespondApiRequest): Either[SocialRouteRespondError, SocialRespondCommand] =
    FriendRequestDecision.fromWire(request.decision.getOrElse("")) match {
      case None =>
        Left(SocialRouteRespondError.InvalidDecision)
      case Some(decision) =>
        parseRequestId(request.requestId) match {
          case Left(error) =>
            Left(error)
          case Right(requestId) =>
            parseRespondActor(request.actorHandle).map(actor => SocialRespondCommand(requestId, actor, decision))
        }
    }

  private def parseCreateHandle(value: Option[String]): Either[SocialRouteCreateError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteCreateError.InvalidHandles)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteCreateError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteCreateError.InvalidHandles)
  }

  private def parseOwnerHandle(value: Option[String]): Either[SocialRouteHandleError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteHandleError.Missing)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteHandleError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteHandleError.Invalid)
  }

  private def parseRequestId(value: Option[String]): Either[SocialRouteRespondError, FriendRequestId] =
    value.map(_.trim).filter(_.nonEmpty).map(FriendRequestId.apply).toRight(SocialRouteRespondError.MissingFields)

  private def parseRespondActor(value: Option[String]): Either[SocialRouteRespondError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteRespondError.MissingFields)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteRespondError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteRespondError.InvalidActorHandle)
  }
}

final case class SocialCreateHandles(
  sourceHandle: PlayerHandle,
  targetHandle: PlayerHandle
)

final case class SocialRespondCommand(
  requestId: FriendRequestId,
  actorHandle: PlayerHandle,
  decision: FriendRequestDecision
)

enum SocialRouteHandleError {
  case Missing
  case VisitorNotAllowed
  case Invalid
}

enum SocialRouteCreateError {
  case InvalidHandles
  case VisitorNotAllowed
}

enum SocialRouteRespondError {
  case InvalidDecision
  case MissingFields
  case InvalidActorHandle
  case VisitorNotAllowed
}
