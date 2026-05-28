package services.forum.api

import io.circe.Error

import services.forum.objects.{ForumReplyId, ForumTopicId}
import services.forum.services.ForumTopicMutationError
import services.identity.objects.PlayerHandle
import system.api.APIMessageError

object ForumAPIMessageSupport {
  def viewerHandle(fields: ForumRequestFields): Option[PlayerHandle] =
    ForumApiTargetParsers.resolveViewerHandle(fields.fields)

  def topicId(fields: ForumRequestFields): Either[APIMessageError, ForumTopicId] =
    requiredText(fields, "topicId").map(ForumTopicId.apply).toRight(error(ForumApiErrorCode.TopicNotFound))

  def replyId(fields: ForumRequestFields): Either[APIMessageError, ForumReplyId] =
    requiredText(fields, "replyId").map(ForumReplyId.apply).toRight(error(ForumApiErrorCode.ReplyNotFound))

  def mutationError(errorValue: ForumTopicMutationError): APIMessageError =
    error(ForumApiErrorMapper.mutationErrorCode(errorValue))

  def mutationParseError(errorValue: ForumTopicMutationParseError): APIMessageError =
    error(ForumApiErrorMapper.mutationErrorCode(errorValue))

  def voteCommandError(errorValue: ForumVoteCommandParseError): APIMessageError =
    errorValue match {
      case ForumVoteCommandParseError.InvalidVote =>
        error(ForumApiErrorCode.InvalidVote)
      case ForumVoteCommandParseError.Mutation(errorValue) =>
        mutationParseError(errorValue)
    }

  def invalidJsonObject(decodeError: Error): APIMessageError =
    error(ForumApiErrorCode.InvalidJsonObject)

  def error(code: ForumApiErrorCode): APIMessageError =
    code match {
      case ForumApiErrorCode.VisitorNotAllowed =>
        APIMessageError.Forbidden(ForumApiErrorCode.message(code))
      case ForumApiErrorCode.TopicNotFound | ForumApiErrorCode.ReplyNotFound =>
        APIMessageError.NotFound(ForumApiErrorCode.message(code))
      case _ =>
        APIMessageError.BadRequest(ForumApiErrorCode.message(code))
    }

  private def requiredText(fields: ForumRequestFields, name: String): Option[String] =
    Option(fields.stringValue(name)).map(_.trim).filter(_.nonEmpty)
}
