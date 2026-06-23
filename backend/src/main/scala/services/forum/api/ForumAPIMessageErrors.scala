package services.forum.api

import cats.effect.IO
import services.forum.objects.ForumTopicView
import services.forum.services.{ForumCreateTopicError, ForumTopicMutationError}
import system.api.APIMessageError

private[api] object ForumAPIMessageErrors {
  def createParse(error: ForumCreateTopicParseError): APIMessageError =
    ForumAPIMessageSupport.error(ForumApiErrorMapper.createErrorCode(error))

  def createService(result: Either[ForumCreateTopicError, ForumTopicView]): IO[ForumTopicView] =
    result.fold(
      error => IO.raiseError(ForumAPIMessageSupport.error(ForumApiErrorMapper.createErrorCode(error))),
      IO.pure
    )

  def mutationParse(error: ForumTopicMutationParseError): APIMessageError =
    ForumAPIMessageSupport.mutationParseError(error)

  def mutationService(result: Either[ForumTopicMutationError, ForumTopicView]): IO[ForumTopicView] =
    result.fold(
      error => IO.raiseError(ForumAPIMessageSupport.mutationError(error)),
      IO.pure
    )

  def voteParse(error: ForumVoteParseError): APIMessageError =
    error match {
      case ForumVoteParseError.InvalidVote =>
        ForumAPIMessageSupport.voteCommandError(ForumVoteCommandParseError.InvalidVote)
    }

  def voteMutationParse(error: ForumTopicMutationParseError): APIMessageError =
    ForumAPIMessageSupport.voteCommandError(ForumVoteCommandParseError.Mutation(error))

  def topicLoad(result: Option[ForumTopicView]): IO[ForumTopicView] =
    result.fold(
      IO.raiseError[ForumTopicView](ForumAPIMessageSupport.error(ForumApiErrorCode.TopicNotFound))
    )(IO.pure)
}
