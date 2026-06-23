package services.forum.api

import io.circe.Error

import services.forum.objects.{ForumReplyId, ForumTopicId}
import services.forum.services.ForumTopicMutationError
import system.api.APIMessageError

object ForumAPIMessageSupport {
  def topicId(value: Option[ForumTopicId]): Either[APIMessageError, ForumTopicId] =
    value.filter(id => id.value.trim.nonEmpty).toRight(error(ForumApiErrorCode.TopicNotFound))

  def replyId(value: Option[ForumReplyId]): Either[APIMessageError, ForumReplyId] =
    value.filter(id => id.value.trim.nonEmpty).toRight(error(ForumApiErrorCode.ReplyNotFound))

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
}
