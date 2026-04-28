package slaydemo.backend.forum.services

import slaydemo.backend.forum.objects.{ForumTopicView, ForumVoteChoice}
import slaydemo.backend.shared.objects.ThreadId

trait ForumService {
  def listTopics(viewerHandle: Option[String] = None): Vector[ForumTopicView]
  def loadTopic(threadId: ThreadId, viewerHandle: Option[String] = None): Option[ForumTopicView]
  def createTopic(title: String, body: String, tag: String, authorHandle: String): Either[String, ForumTopicView]
  def addReply(threadId: ThreadId, body: String, authorHandle: String): Either[String, ForumTopicView]
  def setVote(threadId: ThreadId, authorHandle: String, vote: Option[ForumVoteChoice]): Either[String, ForumTopicView]
  def setReplyVote(
    threadId: ThreadId,
    replyId: String,
    authorHandle: String,
    vote: Option[ForumVoteChoice]
  ): Either[String, ForumTopicView]
}
