package slaydemo.backend.forum.database

import slaydemo.backend.forum.objects.{ForumReplyId, ForumTopicId, ForumTopicRecord}

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
    lock.synchronized {
      topicsById.values.toVector
    }.sortWith(compareRecentFirst)

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

  private def compareRecentFirst(left: ForumTopicRecord, right: ForumTopicRecord): Boolean =
    if left.updatedAt.value != right.updatedAt.value then left.updatedAt.value > right.updatedAt.value
    else left.createdAt.value > right.createdAt.value
}

object InMemoryForumRepository {
  def apply(): InMemoryForumRepository =
    new InMemoryForumRepository()
}
