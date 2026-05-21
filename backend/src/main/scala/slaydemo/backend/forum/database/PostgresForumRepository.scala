package slaydemo.backend.forum.database

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.objects.{
  ForumReplyId,
  ForumTopicId,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVotes
}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresForumRepository(
  settings: PostgresConnectionSettings,
  idGenerator: ForumIdGenerator = RandomForumIdGenerator
) extends ForumRepository {
  PostgresForumSchema.initialize(settings)

  override def nextTopicId(): ForumTopicId =
    idGenerator.nextTopicId()

  override def nextReplyId(): ForumReplyId =
    idGenerator.nextReplyId()

  override def listTopics(): Vector[ForumTopicRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT thread_id, title, body, tag, author_handle, created_at, updated_at
          |FROM forum_topics
          |ORDER BY updated_at DESC, created_at DESC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          val topicIds = Vector.newBuilder[ForumTopicId]
          while resultSet.next() do topicIds += ForumTopicId(resultSet.getString("thread_id"))
          topicIds.result()
        }
      }
    }.flatMap(findTopic)

  override def findTopic(topicId: ForumTopicId): Option[ForumTopicRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresForumRecordReader.readTopicById(connection, topicId)
    }

  override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord =
    PostgresSupport.withTransactionConnection(settings) { connection =>
      PostgresForumTopicQueries.upsertTopic(connection, topic)
      topic.replies.foreach(reply => PostgresForumTopicQueries.upsertReply(connection, topic.id, reply))
      topic
    }

  override def setTopicVote(
    topicId: ForumTopicId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord] =
    PostgresSupport.withTransactionConnection(settings) { connection =>
      if !PostgresForumTopicQueries.topicExists(connection, topicId) then Left(ForumVoteMutationError.TopicNotFound)
      else {
        PostgresForumTopicQueries.updateTopicTimestamp(connection, topicId, updatedAt)
        PostgresForumVoteQueries.writeTopicVote(connection, topicId, authorHandle, vote, updatedAt)
        PostgresForumRecordReader.readTopicById(connection, topicId).toRight(ForumVoteMutationError.TopicNotFound)
      }
    }

  override def setReplyVote(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord] =
    PostgresSupport.withTransactionConnection(settings) { connection =>
      if !PostgresForumTopicQueries.topicExists(connection, topicId) then Left(ForumVoteMutationError.TopicNotFound)
      else if !PostgresForumTopicQueries.replyExists(connection, topicId, replyId) then Left(ForumVoteMutationError.ReplyNotFound)
      else {
        PostgresForumTopicQueries.updateTopicTimestamp(connection, topicId, updatedAt)
        PostgresForumVoteQueries.writeReplyVote(connection, replyId, authorHandle, vote, updatedAt)
        PostgresForumRecordReader.readTopicById(connection, topicId).toRight(ForumVoteMutationError.TopicNotFound)
      }
    }
}
