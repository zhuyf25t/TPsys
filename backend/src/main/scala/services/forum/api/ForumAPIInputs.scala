package services.forum.api

import services.forum.objects.ForumVoteChoice
import services.identity.objects.PlayerHandle
import system.policies.HandlePolicy

enum ForumAuthorInput {
  case Valid(handle: PlayerHandle)
  case Invalid
  case VisitorNotAllowed
}

object ForumAuthorInput {
  def fromWire(value: Option[String]): ForumAuthorInput =
    value.map(HandlePolicy.trim).filter(_.nonEmpty) match {
      case None =>
        ForumAuthorInput.Invalid
      case Some(trimmed) if HandlePolicy.isVisitorLikeHandle(trimmed) =>
        ForumAuthorInput.VisitorNotAllowed
      case Some(trimmed) =>
        PlayerHandle.forLookup(trimmed).map(ForumAuthorInput.Valid.apply).getOrElse(ForumAuthorInput.Invalid)
    }
}

enum ForumVoteInput {
  case Cleared
  case Selected(choice: ForumVoteChoice)
  case Invalid
}

object ForumVoteInput {
  def fromWire(value: Option[String]): ForumVoteInput =
    value.map(_.trim).filter(_.nonEmpty) match {
      case None =>
        ForumVoteInput.Cleared
      case Some(raw) =>
        ForumVoteChoice.fromWire(raw).map(ForumVoteInput.Selected.apply).getOrElse(ForumVoteInput.Invalid)
    }
}

object ForumViewerInput {
  def fromWire(value: Option[String]): Option[PlayerHandle] =
    value
      .map(HandlePolicy.trim)
      .filter(HandlePolicy.isPlayableIdentityHandle)
      .flatMap(PlayerHandle.forLookup)
}

final case class ForumViewerHandleInput(value: Option[PlayerHandle]) extends AnyVal

object ForumViewerHandleInput {
  def fromWire(value: Option[String]): ForumViewerHandleInput =
    ForumViewerHandleInput(ForumViewerInput.fromWire(value))
}
