package services.forum.api

import services.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumVoteChoice
}
import services.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import services.identity.objects.PlayerHandle

object ForumCommandParsers {
  def parseVote(vote: ForumVoteInput): Either[ForumVoteParseError, Option[ForumVoteChoice]] =
    vote match {
      case ForumVoteInput.Cleared =>
        Right(None)
      case ForumVoteInput.Selected(choice) =>
        Right(Some(choice))
      case ForumVoteInput.Invalid =>
        Left(ForumVoteParseError.InvalidVote)
    }

  def parseCreateTopicCommand(
    title: ForumTitle,
    body: ForumBody,
    tag: ForumTag,
    authorHandle: ForumAuthorInput
  ): Either[ForumCreateTopicParseError, CreateForumTopicCommand] =
    for
      parsedTitle <- parseTitle(title.value)
      parsedBody <- parseCreateBody(body.value)
      parsedTag <- parseTag(tag.value)
      author <- parseCreateAuthor(authorHandle)
    yield CreateForumTopicCommand(
      title = parsedTitle,
      body = parsedBody,
      tag = parsedTag,
      authorHandle = author
    )

  def parseAddReplyCommand(
    topicId: ForumTopicId,
    body: ForumBody,
    authorHandle: ForumAuthorInput
  ): Either[ForumTopicMutationParseError, AddForumReplyCommand] =
    for
      parsedBody <- parseReplyBody(body.value)
      author <- parseMutationAuthor(authorHandle)
    yield AddForumReplyCommand(
      topicId = topicId,
      body = parsedBody,
      authorHandle = author
    )

  def parseSetTopicVoteCommand(
    topicId: ForumTopicId,
    authorHandle: ForumAuthorInput,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationParseError, SetForumTopicVoteCommand] =
    for
      author <- parseMutationAuthor(authorHandle)
    yield SetForumTopicVoteCommand(
      topicId = topicId,
      authorHandle = author,
      vote = vote
    )

  def parseSetReplyVoteCommand(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    authorHandle: ForumAuthorInput,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationParseError, SetForumReplyVoteCommand] =
    for
      author <- parseMutationAuthor(authorHandle)
    yield SetForumReplyVoteCommand(
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

  private def parseCreateAuthor(value: ForumAuthorInput): Either[ForumCreateTopicParseError, PlayerHandle] =
    value match {
      case ForumAuthorInput.Valid(handle)      => Right(handle)
      case ForumAuthorInput.Invalid            => Left(ForumCreateTopicParseError.InvalidAuthor)
      case ForumAuthorInput.VisitorNotAllowed => Left(ForumCreateTopicParseError.VisitorNotAllowed)
    }

  private def parseMutationAuthor(value: ForumAuthorInput): Either[ForumTopicMutationParseError, PlayerHandle] =
    value match {
      case ForumAuthorInput.Valid(handle)      => Right(handle)
      case ForumAuthorInput.Invalid            => Left(ForumTopicMutationParseError.InvalidAuthor)
      case ForumAuthorInput.VisitorNotAllowed => Left(ForumTopicMutationParseError.VisitorNotAllowed)
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

enum ForumVoteParseError {
  case InvalidVote
}
