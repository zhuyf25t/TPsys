package slaydemo.backend.forum.database

import slaydemo.backend.forum.objects.{
  ForumReplyRecord,
  ForumReplyVoteRecord,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVoteRecord
}
import slaydemo.backend.shared.objects.ThreadId

trait ForumRepository {
  def listTopics(): Seq[ForumTopicRecord]
  def findTopic(threadId: ThreadId): Option[ForumTopicRecord]
  def saveTopic(topic: ForumTopicRecord): ForumTopicRecord
  def listReplies(threadId: ThreadId): Seq[ForumReplyRecord]
  def saveReply(reply: ForumReplyRecord): ForumReplyRecord
  def listVotes(threadId: ThreadId): Seq[ForumVoteRecord]
  def upsertVote(threadId: ThreadId, authorHandle: String, vote: Option[ForumVoteChoice], updatedAt: Long): Boolean
  def listReplyVotes(replyId: String): Seq[ForumReplyVoteRecord]
  def upsertReplyVote(replyId: String, authorHandle: String, vote: Option[ForumVoteChoice], updatedAt: Long): Boolean
}
