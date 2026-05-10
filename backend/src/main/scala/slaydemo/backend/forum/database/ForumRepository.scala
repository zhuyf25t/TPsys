package slaydemo.backend.forum.database

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.objects.{ForumReplyId, ForumTopicId, ForumTopicRecord, ForumVoteChoice}
import slaydemo.backend.identity.objects.PlayerHandle

enum ForumVoteMutationError {
  case TopicNotFound
  case ReplyNotFound
}

trait ForumRepository {
  def nextTopicId(): ForumTopicId
  def nextReplyId(): ForumReplyId
  def listTopics(): Vector[ForumTopicRecord]
  def findTopic(topicId: ForumTopicId): Option[ForumTopicRecord]
  def saveTopic(topic: ForumTopicRecord): ForumTopicRecord
  def setTopicVote(
    topicId: ForumTopicId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord]
  def setReplyVote(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord]
}
