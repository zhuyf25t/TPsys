package slaydemo.backend.forum.database

import slaydemo.backend.forum.objects.{ForumReplyId, ForumTopicId, ForumTopicRecord}

trait ForumRepository {
  def nextTopicId(): ForumTopicId
  def nextReplyId(): ForumReplyId
  def listTopics(): Vector[ForumTopicRecord]
  def findTopic(topicId: ForumTopicId): Option[ForumTopicRecord]
  def saveTopic(topic: ForumTopicRecord): ForumTopicRecord
}
