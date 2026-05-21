package slaydemo.backend.social.objects.apiTypes

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.social.objects.{FriendRequestDecision, FriendRequestId}

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
