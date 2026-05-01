package slaydemo.backend.forum.database

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.UUID

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumReplyRecord,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVoterKey,
  ForumVotes
}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresForumRepository(settings: PostgresConnectionSettings) extends ForumRepository {
  initialize()

  override def nextTopicId(): ForumTopicId =
    ForumTopicId(s"topic-${UUID.randomUUID().toString}")

  override def nextReplyId(): ForumReplyId =
    ForumReplyId(s"reply-${UUID.randomUUID().toString}")

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
      PostgresSupport.withStatement(
        connection,
        """SELECT thread_id, title, body, tag, author_handle, created_at, updated_at
          |FROM forum_topics
          |WHERE thread_id = ?
          |LIMIT 1""".stripMargin
      ) { statement =>
        statement.setString(1, topicId.value)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then Some(readTopic(connection, resultSet)) else None
        }
      }
    }

  override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord = {
    val connection = PostgresSupport.connect(settings)
    val previousAutoCommit = connection.getAutoCommit
    connection.setAutoCommit(false)
    try {
      upsertTopic(connection, topic)
      topic.replies.foreach(reply => upsertReply(connection, topic.id, reply))
      replaceTopicVotes(connection, topic)
      topic.replies.foreach(reply => replaceReplyVotes(connection, reply))
      connection.commit()
      topic
    } catch {
      case error: Throwable =>
        connection.rollback()
        throw error
    } finally {
      connection.setAutoCommit(previousAutoCommit)
      connection.close()
    }
  }

  private def initialize(): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS forum_topics (
          |  thread_id TEXT PRIMARY KEY,
          |  title TEXT NOT NULL,
          |  body TEXT NOT NULL,
          |  tag TEXT NOT NULL,
          |  author_handle TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  updated_at BIGINT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS forum_replies (
          |  reply_id TEXT PRIMARY KEY,
          |  thread_id TEXT NOT NULL,
          |  author_handle TEXT NOT NULL,
          |  body TEXT NOT NULL,
          |  created_at BIGINT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS forum_votes (
          |  thread_id TEXT NOT NULL,
          |  author_handle TEXT NOT NULL,
          |  vote TEXT NOT NULL,
          |  updated_at BIGINT NOT NULL,
          |  PRIMARY KEY (thread_id, author_handle)
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS forum_reply_votes (
          |  reply_id TEXT NOT NULL,
          |  author_handle TEXT NOT NULL,
          |  vote TEXT NOT NULL,
          |  updated_at BIGINT NOT NULL,
          |  PRIMARY KEY (reply_id, author_handle)
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS forum_topics_updated_at_idx ON forum_topics (updated_at DESC, created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS forum_replies_thread_id_created_at_idx ON forum_replies (thread_id, created_at ASC)"
      )(_.executeUpdate())
    }

  private def upsertTopic(connection: Connection, topic: ForumTopicRecord): Unit =
    PostgresSupport.withStatement(
      connection,
      """INSERT INTO forum_topics (
        |  thread_id, title, body, tag, author_handle, created_at, updated_at
        |) VALUES (?, ?, ?, ?, ?, ?, ?)
        |ON CONFLICT (thread_id) DO UPDATE SET
        |  title = EXCLUDED.title,
        |  body = EXCLUDED.body,
        |  tag = EXCLUDED.tag,
        |  author_handle = EXCLUDED.author_handle,
        |  created_at = EXCLUDED.created_at,
        |  updated_at = EXCLUDED.updated_at""".stripMargin
    ) { statement =>
      statement.setString(1, topic.id.value)
      statement.setString(2, topic.title.value)
      statement.setString(3, topic.body.value)
      statement.setString(4, topic.tag.value)
      statement.setString(5, topic.authorHandle.value)
      statement.setLong(6, topic.createdAt.value)
      statement.setLong(7, topic.updatedAt.value)
      statement.executeUpdate()
    }

  private def upsertReply(connection: Connection, topicId: ForumTopicId, reply: ForumReplyRecord): Unit =
    PostgresSupport.withStatement(
      connection,
      """INSERT INTO forum_replies (
        |  reply_id, thread_id, author_handle, body, created_at
        |) VALUES (?, ?, ?, ?, ?)
        |ON CONFLICT (reply_id) DO UPDATE SET
        |  thread_id = EXCLUDED.thread_id,
        |  author_handle = EXCLUDED.author_handle,
        |  body = EXCLUDED.body,
        |  created_at = EXCLUDED.created_at""".stripMargin
    ) { statement =>
      statement.setString(1, reply.id.value)
      statement.setString(2, topicId.value)
      statement.setString(3, reply.authorHandle.value)
      statement.setString(4, reply.body.value)
      statement.setLong(5, reply.createdAt.value)
      statement.executeUpdate()
    }

  private def replaceTopicVotes(connection: Connection, topic: ForumTopicRecord): Unit = {
    PostgresSupport.withStatement(connection, "DELETE FROM forum_votes WHERE thread_id = ?") { statement =>
      statement.setString(1, topic.id.value)
      statement.executeUpdate()
    }

    topic.votes.valuesByVoter.foreach { case (voter, vote) =>
      PostgresSupport.withStatement(
        connection,
        "INSERT INTO forum_votes (thread_id, author_handle, vote, updated_at) VALUES (?, ?, ?, ?)"
      ) { statement =>
        statement.setString(1, topic.id.value)
        statement.setString(2, voter.value)
        statement.setString(3, ForumVoteChoice.wireValue(vote))
        statement.setLong(4, topic.updatedAt.value)
        statement.executeUpdate()
      }
    }
  }

  private def replaceReplyVotes(connection: Connection, reply: ForumReplyRecord): Unit = {
    PostgresSupport.withStatement(connection, "DELETE FROM forum_reply_votes WHERE reply_id = ?") { statement =>
      statement.setString(1, reply.id.value)
      statement.executeUpdate()
    }

    reply.votes.valuesByVoter.foreach { case (voter, vote) =>
      PostgresSupport.withStatement(
        connection,
        "INSERT INTO forum_reply_votes (reply_id, author_handle, vote, updated_at) VALUES (?, ?, ?, ?)"
      ) { statement =>
        statement.setString(1, reply.id.value)
        statement.setString(2, voter.value)
        statement.setString(3, ForumVoteChoice.wireValue(vote))
        statement.setLong(4, reply.createdAt.value)
        statement.executeUpdate()
      }
    }
  }

  private def readTopic(connection: Connection, resultSet: ResultSet): ForumTopicRecord = {
    val topicId = ForumTopicId(resultSet.getString("thread_id"))
    ForumTopicRecord(
      id = topicId,
      title = ForumTitle(resultSet.getString("title")),
      body = ForumBody(resultSet.getString("body")),
      tag = ForumTag(resultSet.getString("tag")),
      authorHandle = PlayerHandle(resultSet.getString("author_handle")),
      createdAt = EpochMillis(resultSet.getLong("created_at")),
      updatedAt = EpochMillis(resultSet.getLong("updated_at")),
      replies = readReplies(connection, topicId),
      votes = readTopicVotes(connection, topicId)
    )
  }

  private def readReplies(connection: Connection, topicId: ForumTopicId): Vector[ForumReplyRecord] =
    PostgresSupport.withStatement(
      connection,
      """SELECT reply_id, author_handle, body, created_at
        |FROM forum_replies
        |WHERE thread_id = ?
        |ORDER BY created_at ASC, reply_id ASC""".stripMargin
    ) { statement =>
      statement.setString(1, topicId.value)
      PostgresSupport.withResultSet(statement) { resultSet =>
        val replies = Vector.newBuilder[ForumReplyRecord]
        while resultSet.next() do {
          val replyId = ForumReplyId(resultSet.getString("reply_id"))
          replies += ForumReplyRecord(
            id = replyId,
            authorHandle = PlayerHandle(resultSet.getString("author_handle")),
            body = ForumBody(resultSet.getString("body")),
            createdAt = EpochMillis(resultSet.getLong("created_at")),
            votes = readReplyVotes(connection, replyId)
          )
        }
        replies.result()
      }
    }

  private def readTopicVotes(connection: Connection, topicId: ForumTopicId): ForumVotes =
    readVotes(
      connection = connection,
      sql = "SELECT author_handle, vote FROM forum_votes WHERE thread_id = ?",
      bind = statement => statement.setString(1, topicId.value)
    )

  private def readReplyVotes(connection: Connection, replyId: ForumReplyId): ForumVotes =
    readVotes(
      connection = connection,
      sql = "SELECT author_handle, vote FROM forum_reply_votes WHERE reply_id = ?",
      bind = statement => statement.setString(1, replyId.value)
    )

  private def readVotes(connection: Connection, sql: String, bind: PreparedStatement => Unit): ForumVotes =
    PostgresSupport.withStatement(connection, sql) { statement =>
      bind(statement)
      PostgresSupport.withResultSet(statement) { resultSet =>
        var votes = Map.empty[ForumVoterKey, ForumVoteChoice]
        while resultSet.next() do {
          val voter = PlayerHandle(resultSet.getString("author_handle"))
          ForumVoteChoice.fromWire(resultSet.getString("vote")).foreach { vote =>
            votes = votes.updated(ForumVoterKey.fromHandle(voter), vote)
          }
        }
        ForumVotes(votes)
      }
    }
}
