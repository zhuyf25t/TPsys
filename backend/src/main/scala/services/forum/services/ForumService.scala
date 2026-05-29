package services.forum.services

import cats.effect.IO

import services.battle.objects.EpochMillis
import services.forum.database.{ForumRepository, ForumVoteMutationError, InMemoryForumRepository}
import services.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumReplyRecord,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumTopicRecord,
  ForumTopicView,
  ForumVoteChoice
}
import services.identity.objects.PlayerHandle
import system.policies.HandlePolicy

enum ForumCreateTopicError {
  case InvalidTitle
  case InvalidBody
  case InvalidTag
  case InvalidAuthor
  case VisitorNotAllowed
}

enum ForumTopicMutationError {
  case TopicNotFound
  case ReplyNotFound
}

final case class CreateForumTopicCommand(
  title: ForumTitle,
  body: ForumBody,
  tag: ForumTag,
  authorHandle: PlayerHandle
)

final case class AddForumReplyCommand(
  topicId: ForumTopicId,
  body: ForumBody,
  authorHandle: PlayerHandle
)

final case class SetForumTopicVoteCommand(
  topicId: ForumTopicId,
  authorHandle: PlayerHandle,
  vote: Option[ForumVoteChoice]
)

final case class SetForumReplyVoteCommand(
  topicId: ForumTopicId,
  replyId: ForumReplyId,
  authorHandle: PlayerHandle,
  vote: Option[ForumVoteChoice]
)

trait ForumService {
  def listTopics(viewerHandle: Option[PlayerHandle]): IO[Vector[ForumTopicView]]
  def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): IO[Option[ForumTopicView]]
  def createTopic(command: CreateForumTopicCommand): IO[Either[ForumCreateTopicError, ForumTopicView]]
  def addReply(command: AddForumReplyCommand): IO[Either[ForumTopicMutationError, ForumTopicView]]
  def setTopicVote(command: SetForumTopicVoteCommand): IO[Either[ForumTopicMutationError, ForumTopicView]]
  def setReplyVote(command: SetForumReplyVoteCommand): IO[Either[ForumTopicMutationError, ForumTopicView]]
}

final class DefaultForumService(repository: ForumRepository, currentTimeMillis: () => Long) extends ForumService {
  override def listTopics(viewerHandle: Option[PlayerHandle]): IO[Vector[ForumTopicView]] =
    IO.blocking(repository.listTopics().map(ForumTopicRecord.toView(_, viewerHandle)))

  override def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): IO[Option[ForumTopicView]] =
    IO.blocking(repository.findTopic(topicId).map(ForumTopicRecord.toView(_, viewerHandle)))

  override def createTopic(command: CreateForumTopicCommand): IO[Either[ForumCreateTopicError, ForumTopicView]] =
    for
      titleResult <- IO.pure(validateTitle(command.title))
      result <- titleResult match {
        case Left(error) =>
          IO.pure(Left(error))
        case Right(title) =>
          for
            bodyResult <- IO.pure(validateCreateBody(command.body))
            result <- bodyResult match {
              case Left(error) =>
                IO.pure(Left(error))
              case Right(body) =>
                for
                  tagResult <- IO.pure(validateTag(command.tag))
                  result <- tagResult match {
                    case Left(error) =>
                      IO.pure(Left(error))
                    case Right(tag) =>
                      for
                        authorResult <- IO.pure(validateCreateAuthor(command.authorHandle))
                        result <- authorResult match {
                          case Left(error) =>
                            IO.pure(Left(error))
                          case Right(author) =>
                            for
                              topic <- createParsedTopic(title, body, tag, author)
                              result <- IO.pure(Right(topic))
                            yield result
                        }
                      yield result
                  }
                yield result
            }
          yield result
      }
    yield result

  override def addReply(command: AddForumReplyCommand): IO[Either[ForumTopicMutationError, ForumTopicView]] =
    IO.blocking(addParsedReply(command.topicId, command.body, command.authorHandle))

  override def setTopicVote(command: SetForumTopicVoteCommand): IO[Either[ForumTopicMutationError, ForumTopicView]] =
    IO.blocking(setParsedTopicVote(command.topicId, command.authorHandle, command.vote))

  override def setReplyVote(command: SetForumReplyVoteCommand): IO[Either[ForumTopicMutationError, ForumTopicView]] =
    IO.blocking(setParsedReplyVote(command.topicId, command.replyId, command.authorHandle, command.vote))

  private def createParsedTopic(
    title: ForumTitle,
    body: ForumBody,
    tag: ForumTag,
    author: PlayerHandle
  ): IO[ForumTopicView] =
    IO.blocking {
      val now = EpochMillis(currentTimeMillis())
      val topic = ForumTopicRecord.create(
        id = repository.nextTopicId(),
        title = title,
        body = body,
        tag = tag,
        authorHandle = author,
        createdAt = now
      )
      ForumTopicRecord.toView(repository.saveTopic(topic), Some(author))
    }

  private def validateTitle(value: ForumTitle): Either[ForumCreateTopicError, ForumTitle] =
    Option(value.value).map(_.trim).filter(_.nonEmpty).map(ForumTitle.apply).toRight(ForumCreateTopicError.InvalidTitle)

  private def validateCreateBody(value: ForumBody): Either[ForumCreateTopicError, ForumBody] =
    Option(value.value).map(_.trim).filter(_.nonEmpty).map(ForumBody.apply).toRight(ForumCreateTopicError.InvalidBody)

  private def validateTag(value: ForumTag): Either[ForumCreateTopicError, ForumTag] =
    Option(value.value).map(_.trim).filter(_.nonEmpty).map(ForumTag.apply).toRight(ForumCreateTopicError.InvalidTag)

  private def validateCreateAuthor(value: PlayerHandle): Either[ForumCreateTopicError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value.value)
    if trimmed.isEmpty then Left(ForumCreateTopicError.InvalidAuthor)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(ForumCreateTopicError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ForumCreateTopicError.InvalidAuthor)
  }

  private def addParsedReply(
    topicId: ForumTopicId,
    body: ForumBody,
    author: PlayerHandle
  ): Either[ForumTopicMutationError, ForumTopicView] =
    repository.findTopic(topicId) match {
      case None =>
        Left(ForumTopicMutationError.TopicNotFound)
      case Some(topic) =>
        val now = EpochMillis(currentTimeMillis())
        val reply = ForumReplyRecord.create(
          id = repository.nextReplyId(),
          authorHandle = author,
          body = body,
          createdAt = now
        )
        val updated = ForumTopicRecord.addReply(topic, reply, now)
        Right(ForumTopicRecord.toView(repository.saveTopic(updated), Some(author)))
    }

  private def setParsedTopicVote(
    topicId: ForumTopicId,
    author: PlayerHandle,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationError, ForumTopicView] =
    repository
      .setTopicVote(topicId, author, vote, EpochMillis(currentTimeMillis()))
      .left
      .map(toMutationError)
      .map(ForumTopicRecord.toView(_, Some(author)))

  private def setParsedReplyVote(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    author: PlayerHandle,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationError, ForumTopicView] =
    repository
      .setReplyVote(topicId, replyId, author, vote, EpochMillis(currentTimeMillis()))
      .left
      .map(toMutationError)
      .map(ForumTopicRecord.toView(_, Some(author)))

  private def toMutationError(error: ForumVoteMutationError): ForumTopicMutationError =
    error match {
      case ForumVoteMutationError.TopicNotFound => ForumTopicMutationError.TopicNotFound
      case ForumVoteMutationError.ReplyNotFound => ForumTopicMutationError.ReplyNotFound
    }

}

object DefaultForumService {
  def apply(repository: ForumRepository, currentTimeMillis: () => Long): DefaultForumService =
    new DefaultForumService(repository, currentTimeMillis)
}

object InMemoryForumService {
  def apply(): DefaultForumService =
    DefaultForumService(InMemoryForumRepository(), () => System.currentTimeMillis())
}
