package slaydemo.backend.forum.database

import java.util.UUID

import slaydemo.backend.forum.objects.{ForumReplyId, ForumTopicId}

private[database] trait ForumIdGenerator {
  def nextTopicId(): ForumTopicId
  def nextReplyId(): ForumReplyId
}

private[database] object RandomForumIdGenerator extends ForumIdGenerator {
  override def nextTopicId(): ForumTopicId =
    ForumTopicId(s"topic-${UUID.randomUUID().toString}")

  override def nextReplyId(): ForumReplyId =
    ForumReplyId(s"reply-${UUID.randomUUID().toString}")
}
