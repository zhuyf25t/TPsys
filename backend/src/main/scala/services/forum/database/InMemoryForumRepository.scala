package services.forum.database

import services.battle.objects.EpochMillis
import services.forum.objects.{
  ForumReplyId,
  ForumReplyVoteUpdateError,
  ForumTopicId,
  ForumTopicRecord,
  ForumVoteChoice
}
import services.identity.objects.PlayerHandle

final class InMemoryForumRepository extends ForumRepository {
  private val lock = Object()
  private var topicsById: Map[ForumTopicId, ForumTopicRecord] = Map.empty
  private var nextTopicNumber: Long = 1L
  private var nextReplyNumber: Long = 1L

  override def nextTopicId(): ForumTopicId =
    lock.synchronized {
      val id = ForumTopicId(f"topic-$nextTopicNumber%012d")
      nextTopicNumber += 1L
      id
    }

  override def nextReplyId(): ForumReplyId =
    lock.synchronized {
      val id = ForumReplyId(f"reply-$nextReplyNumber%012d")
      nextReplyNumber += 1L
      id
    }

  override def listTopics(): Vector[ForumTopicRecord] =
    ForumTopicOrderingRules.sortRecentFirst(
      lock.synchronized {
        topicsById.values.toVector
      }
    )

  override def findTopic(topicId: ForumTopicId): Option[ForumTopicRecord] =
    lock.synchronized {
      topicsById.get(topicId)
    }

  override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord = {
    lock.synchronized {
      topicsById = topicsById.updated(topic.id, topic)
    }
    topic
  }

  override def setTopicVote(
    topicId: ForumTopicId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord] =
    lock.synchronized {
      topicsById.get(topicId) match {
        case None =>
          Left(ForumVoteMutationError.TopicNotFound)
        case Some(topic) =>
          val updated = ForumTopicRecord.setVote(topic, authorHandle, vote, updatedAt)
          topicsById = topicsById.updated(topicId, updated)
          Right(updated)
      }
    }

  override def setReplyVote(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord] =
    lock.synchronized {
      topicsById.get(topicId) match {
        case None =>
          Left(ForumVoteMutationError.TopicNotFound)
        case Some(topic) =>
          ForumTopicRecord.setReplyVote(topic, replyId, authorHandle, vote, updatedAt) match {
            case Left(ForumReplyVoteUpdateError.ReplyNotFound) =>
              Left(ForumVoteMutationError.ReplyNotFound)
            case Right(updated) =>
              topicsById = topicsById.updated(topicId, updated)
              Right(updated)
          }
      }
    }

}

object InMemoryForumRepository {
  def apply(): InMemoryForumRepository =
    new InMemoryForumRepository()
}
