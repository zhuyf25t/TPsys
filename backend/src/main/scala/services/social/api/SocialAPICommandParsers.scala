package services.social.api

import services.identity.objects.PlayerHandle
import services.social.objects.{FriendRequestDecision, FriendRequestId}
import system.policies.HandlePolicy

object FriendRequestOwnerQuery {
  def parseFromQuery(query: Map[String, String]): Either[SocialRouteHandleError, PlayerHandle] =
    parse(query.get("ownerHandle").flatMap(SocialAPIMessageDecoding.playerHandleFromWire))

  def parse(ownerHandle: Option[PlayerHandle]): Either[SocialRouteHandleError, PlayerHandle] =
    SocialCommandParsers.parseOwner(ownerHandle)
}

object SocialCommandParsers {
  def parseOwner(ownerHandle: Option[PlayerHandle]): Either[SocialRouteHandleError, PlayerHandle] =
    parseOwnerHandle(ownerHandle)

  def parseCreateHandles(message: FriendRequestCreateAPIMessage): Either[SocialRouteCreateError, SocialCreateHandles] =
    parseCreateHandle(message.sourceHandle) match {
      case Left(error) =>
        Left(error)
      case Right(source) =>
        parseCreateHandle(message.targetHandle).map(target => SocialCreateHandles(source, target))
    }

  def parseRespondCommand(message: FriendRequestRespondAPIMessage): Either[SocialRouteRespondError, SocialRespondCommand] =
    message.decision match {
      case None =>
        Left(SocialRouteRespondError.InvalidDecision)
      case Some(decision) =>
        parseRequestId(message.requestId) match {
          case Left(error) =>
            Left(error)
          case Right(requestId) =>
            parseRespondActor(message.actorHandle).map(actor => SocialRespondCommand(requestId, actor, decision))
        }
    }

  private def parseCreateHandle(value: Option[PlayerHandle]): Either[SocialRouteCreateError, PlayerHandle] = {
    val trimmed = value.map(handle => HandlePolicy.trim(handle.value)).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteCreateError.InvalidHandles)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteCreateError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteCreateError.InvalidHandles)
  }

  private def parseOwnerHandle(value: Option[PlayerHandle]): Either[SocialRouteHandleError, PlayerHandle] = {
    val trimmed = value.map(handle => HandlePolicy.trim(handle.value)).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteHandleError.Missing)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteHandleError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteHandleError.Invalid)
  }

  private def parseRequestId(value: Option[FriendRequestId]): Either[SocialRouteRespondError, FriendRequestId] =
    value.filter(_.value.trim.nonEmpty).toRight(SocialRouteRespondError.MissingFields)

  private def parseRespondActor(value: Option[PlayerHandle]): Either[SocialRouteRespondError, PlayerHandle] = {
    val trimmed = value.map(handle => HandlePolicy.trim(handle.value)).getOrElse("")
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
