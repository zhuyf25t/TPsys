package slaydemo.backend.forum.services

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.database.{ForumRepository, InMemoryForumRepository}
import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumReplyRecord,
  ForumReplyVoteUpdateError,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumTopicRecord,
  ForumTopicView,
  ForumVoteChoice
}
import slaydemo.backend.identity.objects.PlayerHandle

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
  def listTopics(viewerHandle: Option[PlayerHandle]): Vector[ForumTopicView]
  def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): Option[ForumTopicView]
  def createTopic(command: CreateForumTopicCommand): ForumTopicView
  def addReply(command: AddForumReplyCommand): Either[ForumTopicMutationError, ForumTopicView]
  def setTopicVote(command: SetForumTopicVoteCommand): Either[ForumTopicMutationError, ForumTopicView]
  def setReplyVote(command: SetForumReplyVoteCommand): Either[ForumTopicMutationError, ForumTopicView]
}

final class DefaultForumService(repository: ForumRepository, currentTimeMillis: () => Long) extends ForumService {
  override def listTopics(viewerHandle: Option[PlayerHandle]): Vector[ForumTopicView] =
    repository.listTopics().map(ForumTopicRecord.toView(_, viewerHandle))

  override def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): Option[ForumTopicView] =
    repository.findTopic(topicId).map(ForumTopicRecord.toView(_, viewerHandle))

  override def createTopic(command: CreateForumTopicCommand): ForumTopicView =
    createParsedTopic(command.title, command.body, command.tag, command.authorHandle)

  override def addReply(command: AddForumReplyCommand): Either[ForumTopicMutationError, ForumTopicView] =
    addParsedReply(command.topicId, command.body, command.authorHandle)

  override def setTopicVote(command: SetForumTopicVoteCommand): Either[ForumTopicMutationError, ForumTopicView] =
    setParsedTopicVote(command.topicId, command.authorHandle, command.vote)

  override def setReplyVote(command: SetForumReplyVoteCommand): Either[ForumTopicMutationError, ForumTopicView] =
    setParsedReplyVote(command.topicId, command.replyId, command.authorHandle, command.vote)

  private def createParsedTopic(
    title: ForumTitle,
    body: ForumBody,
    tag: ForumTag,
    author: PlayerHandle
  ): ForumTopicView =
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
    repository.findTopic(topicId) match {
      case None =>
        Left(ForumTopicMutationError.TopicNotFound)
      case Some(topic) =>
        val updated = ForumTopicRecord.setVote(topic, author, vote, EpochMillis(currentTimeMillis()))
        Right(ForumTopicRecord.toView(repository.saveTopic(updated), Some(author)))
    }

  private def setParsedReplyVote(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    author: PlayerHandle,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationError, ForumTopicView] =
    repository.findTopic(topicId) match {
      case None =>
        Left(ForumTopicMutationError.TopicNotFound)
      case Some(topic) =>
        ForumTopicRecord.setReplyVote(topic, replyId, author, vote, EpochMillis(currentTimeMillis())) match {
          case Left(ForumReplyVoteUpdateError.ReplyNotFound) =>
            Left(ForumTopicMutationError.ReplyNotFound)
          case Right(updated) =>
            Right(ForumTopicRecord.toView(repository.saveTopic(updated), Some(author)))
        }
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
