package slaydemo.backend.forum.objects.apiTypes

import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumVoteChoice
}
import slaydemo.backend.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.policies.HandlePolicy

object ForumCommandParsers {
  def parseVote(fields: ForumRequestFields): Either[String, Option[ForumVoteChoice]] =
    fields.fields.get("vote") match {
      case None if !fields.voteSeen =>
        Right(None)
      case Some(raw) if raw.trim.isEmpty =>
        Right(None)
      case Some(raw) =>
        ForumVoteChoice.fromWire(raw).map(Some(_)).toRight("invalid_vote")
      case None =>
        Right(None)
    }

  def parseCreateTopicCommand(fields: ForumRequestFields): Either[ForumCreateTopicParseError, CreateForumTopicCommand] =
    for {
      title <- parseTitle(fields.stringValue("title"))
      body <- parseCreateBody(fields.stringValue("body"))
      tag <- parseTag(fields.stringValue("tag"))
      author <- parseCreateAuthor(fields.stringValue("author"))
    } yield CreateForumTopicCommand(
      title = title,
      body = body,
      tag = tag,
      authorHandle = author
    )

  def parseAddReplyCommand(
    topicId: ForumTopicId,
    fields: ForumRequestFields
  ): Either[ForumTopicMutationParseError, AddForumReplyCommand] =
    for {
      body <- parseReplyBody(fields.stringValue("body"))
      author <- parseMutationAuthor(fields.stringValue("author"))
    } yield AddForumReplyCommand(
      topicId = topicId,
      body = body,
      authorHandle = author
    )

  def parseSetTopicVoteCommand(
    topicId: ForumTopicId,
    fields: ForumRequestFields,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationParseError, SetForumTopicVoteCommand] =
    for {
      author <- parseMutationAuthor(fields.stringValue("author"))
    } yield SetForumTopicVoteCommand(
      topicId = topicId,
      authorHandle = author,
      vote = vote
    )

  def parseSetReplyVoteCommand(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    fields: ForumRequestFields,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationParseError, SetForumReplyVoteCommand] =
    for {
      author <- parseMutationAuthor(fields.stringValue("author"))
    } yield SetForumReplyVoteCommand(
      topicId = topicId,
      replyId = replyId,
      authorHandle = author,
      vote = vote
    )

  private def parseTitle(value: String): Either[ForumCreateTopicParseError, ForumTitle] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumTitle.apply).toRight(ForumCreateTopicParseError.InvalidTitle)

  private def parseCreateBody(value: String): Either[ForumCreateTopicParseError, ForumBody] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumBody.apply).toRight(ForumCreateTopicParseError.InvalidBody)

  private def parseReplyBody(value: String): Either[ForumTopicMutationParseError, ForumBody] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumBody.apply).toRight(ForumTopicMutationParseError.InvalidBody)

  private def parseTag(value: String): Either[ForumCreateTopicParseError, ForumTag] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumTag.apply).toRight(ForumCreateTopicParseError.InvalidTag)

  private def parseCreateAuthor(value: String): Either[ForumCreateTopicParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ForumCreateTopicParseError.InvalidAuthor)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(ForumCreateTopicParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ForumCreateTopicParseError.InvalidAuthor)
  }

  private def parseMutationAuthor(value: String): Either[ForumTopicMutationParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ForumTopicMutationParseError.InvalidAuthor)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(ForumTopicMutationParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ForumTopicMutationParseError.InvalidAuthor)
  }

}

enum ForumCreateTopicParseError {
  case InvalidTitle
  case InvalidBody
  case InvalidTag
  case InvalidAuthor
  case VisitorNotAllowed
}

enum ForumTopicMutationParseError {
  case TopicNotFound
  case ReplyNotFound
  case InvalidBody
  case InvalidAuthor
  case VisitorNotAllowed
}
