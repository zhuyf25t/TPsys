package services.forum.api

import services.forum.objects.{ForumReplyId, ForumTopicId}
import services.forum.objects.apiTypes.{ForumApiRequestFields, ForumVoteFieldPresence}
import services.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}

final case class ForumRequestFields(fields: Map[String, String], votePresence: ForumVoteFieldPresence) {
  def stringValue(name: String): String =
    fields.getOrElse(name, "")

  def toCreateTopicCommand: Either[ForumCreateTopicParseError, CreateForumTopicCommand] =
    ForumCommandParsers.parseCreateTopicCommand(this)

  def toAddReplyCommand(topicId: ForumTopicId): Either[ForumTopicMutationParseError, AddForumReplyCommand] =
    ForumCommandParsers.parseAddReplyCommand(topicId, this)

  def toSetTopicVoteCommand(topicId: ForumTopicId): Either[ForumVoteCommandParseError, SetForumTopicVoteCommand] =
    ForumCommandParsers.parseVote(this).left.map(voteParseApiError).flatMap { vote =>
      ForumCommandParsers.parseSetTopicVoteCommand(topicId, this, vote)
        .left.map(ForumVoteCommandParseError.Mutation.apply)
    }

  def toSetReplyVoteCommand(topicId: ForumTopicId, replyId: ForumReplyId): Either[ForumVoteCommandParseError, SetForumReplyVoteCommand] =
    ForumCommandParsers.parseVote(this).left.map(voteParseApiError).flatMap { vote =>
      ForumCommandParsers.parseSetReplyVoteCommand(topicId, replyId, this, vote)
        .left.map(ForumVoteCommandParseError.Mutation.apply)
    }

  private def voteParseApiError(error: ForumVoteParseError): ForumVoteCommandParseError =
    error match {
      case ForumVoteParseError.InvalidVote => ForumVoteCommandParseError.InvalidVote
    }
}

object ForumRequestFields {
  def fromApi(fields: ForumApiRequestFields): ForumRequestFields =
    ForumRequestFields(fields.fields, fields.votePresence)
}

enum ForumVoteCommandParseError {
  case InvalidVote
  case Mutation(error: ForumTopicMutationParseError)
}
