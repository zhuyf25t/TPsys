package slaydemo.backend.forum.database

import java.sql.{PreparedStatement, ResultSet, Types}

import slaydemo.backend.forum.objects.{
  ForumReplyRecord,
  ForumReplyVoteRecord,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVoteRecord
}
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}
import slaydemo.backend.shared.objects.ThreadId

final class PostgresForumRepository(config: PostgresConfig) extends ForumRepository {
  initialize()

  override def listTopics(): Seq[ForumTopicRecord] = queryTopics(
    """SELECT thread_id, title, body, tag, author_handle, created_at, updated_at
      |FROM forum_topics
      |ORDER BY updated_at DESC, created_at DESC""".stripMargin
  )

  override def findTopic(threadId: ThreadId): Option[ForumTopicRecord] = queryTopic(
    """SELECT thread_id, title, body, tag, author_handle, created_at, updated_at
      |FROM forum_topics
      |WHERE thread_id = ?""".stripMargin,
    statement => statement.setString(1, threadId.value)
  )

  override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord = {
    PostgresSupport.withConnection(config) { connection =>
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
        bindTopic(statement, topic)
        statement.executeUpdate()
      }
    }

    topic
  }

  override def listReplies(threadId: ThreadId): Seq[ForumReplyRecord] = queryReplies(
    """SELECT reply_id, thread_id, author_handle, body, created_at
      |FROM forum_replies
      |WHERE thread_id = ?
      |ORDER BY created_at ASC, reply_id ASC""".stripMargin,
    statement => statement.setString(1, threadId.value)
  )

  override def saveReply(reply: ForumReplyRecord): ForumReplyRecord = {
    PostgresSupport.withConnection(config) { connection =>
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
        bindReply(statement, reply)
        statement.executeUpdate()
      }
    }

    reply
  }

  override def listVotes(threadId: ThreadId): Seq[ForumVoteRecord] = queryVotes(
    """SELECT thread_id, author_handle, vote, updated_at
      |FROM forum_votes
      |WHERE thread_id = ?""".stripMargin,
    statement => statement.setString(1, threadId.value)
  )

  override def upsertVote(threadId: ThreadId, authorHandle: String, vote: Option[ForumVoteChoice], updatedAt: Long): Boolean = {
    val keyVote = findVote(threadId, authorHandle)
    vote match {
      case Some(choice) if keyVote.forall(_.vote != choice) =>
        PostgresSupport.withConnection(config) { connection =>
          PostgresSupport.withStatement(
            connection,
            """INSERT INTO forum_votes (thread_id, author_handle, vote, updated_at)
              |VALUES (?, ?, ?, ?)
              |ON CONFLICT (thread_id, author_handle) DO UPDATE SET
              |  vote = EXCLUDED.vote,
              |  updated_at = EXCLUDED.updated_at""".stripMargin
          ) { statement =>
            statement.setString(1, threadId.value)
            statement.setString(2, authorHandle)
            statement.setString(3, choice.value)
            statement.setLong(4, updatedAt)
            statement.executeUpdate()
          }
        }
        true
      case Some(_) =>
        false
      case None if keyVote.isDefined =>
        PostgresSupport.withConnection(config) { connection =>
          PostgresSupport.withStatement(
            connection,
            "DELETE FROM forum_votes WHERE thread_id = ? AND author_handle = ?"
          ) { statement =>
            statement.setString(1, threadId.value)
            statement.setString(2, authorHandle)
            statement.executeUpdate()
          }
        }
        true
      case None =>
        false
    }
  }

  override def listReplyVotes(replyId: String): Seq[ForumReplyVoteRecord] = queryReplyVotes(
    """SELECT reply_id, author_handle, vote, updated_at
      |FROM forum_reply_votes
      |WHERE reply_id = ?""".stripMargin,
    statement => statement.setString(1, replyId)
  )

  override def upsertReplyVote(replyId: String, authorHandle: String, vote: Option[ForumVoteChoice], updatedAt: Long): Boolean = {
    val keyVote = findReplyVote(replyId, authorHandle)
    vote match {
      case Some(choice) if keyVote.forall(_.vote != choice) =>
        PostgresSupport.withConnection(config) { connection =>
          PostgresSupport.withStatement(
            connection,
            """INSERT INTO forum_reply_votes (reply_id, author_handle, vote, updated_at)
              |VALUES (?, ?, ?, ?)
              |ON CONFLICT (reply_id, author_handle) DO UPDATE SET
              |  vote = EXCLUDED.vote,
              |  updated_at = EXCLUDED.updated_at""".stripMargin
          ) { statement =>
            statement.setString(1, replyId)
            statement.setString(2, authorHandle)
            statement.setString(3, choice.value)
            statement.setLong(4, updatedAt)
            statement.executeUpdate()
          }
        }
        true
      case Some(_) =>
        false
      case None if keyVote.isDefined =>
        PostgresSupport.withConnection(config) { connection =>
          PostgresSupport.withStatement(
            connection,
            "DELETE FROM forum_reply_votes WHERE reply_id = ? AND author_handle = ?"
          ) { statement =>
            statement.setString(1, replyId)
            statement.setString(2, authorHandle)
            statement.executeUpdate()
          }
        }
        true
      case None =>
        false
    }
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
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

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS forum_votes_thread_id_idx ON forum_votes (thread_id, author_handle)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS forum_reply_votes_reply_id_idx ON forum_reply_votes (reply_id, author_handle)"
      )(_.executeUpdate())
    }
  }

  private def queryTopics(sql: String): Seq[ForumTopicRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          readTopics(resultSet)
        }
      }
    }
  }

  private def queryTopic(sql: String, bind: PreparedStatement => Unit): Option[ForumTopicRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) Some(readTopic(resultSet)) else None
        }
      }
    }
  }

  private def queryReplies(sql: String, bind: PreparedStatement => Unit): Seq[ForumReplyRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          readReplies(resultSet)
        }
      }
    }
  }

  private def queryVotes(sql: String, bind: PreparedStatement => Unit): Seq[ForumVoteRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          readVotes(resultSet)
        }
      }
    }
  }

  private def queryReplyVotes(sql: String, bind: PreparedStatement => Unit): Seq[ForumReplyVoteRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          readReplyVotes(resultSet)
        }
      }
    }
  }

  private def findVote(threadId: ThreadId, authorHandle: String): Option[ForumVoteRecord] = {
    queryVotes(
      """SELECT thread_id, author_handle, vote, updated_at
        |FROM forum_votes
        |WHERE thread_id = ? AND author_handle = ?""".stripMargin,
      statement => {
        statement.setString(1, threadId.value)
        statement.setString(2, authorHandle)
      }
    ).headOption
  }

  private def findReplyVote(replyId: String, authorHandle: String): Option[ForumReplyVoteRecord] = {
    queryReplyVotes(
      """SELECT reply_id, author_handle, vote, updated_at
        |FROM forum_reply_votes
        |WHERE reply_id = ? AND author_handle = ?""".stripMargin,
      statement => {
        statement.setString(1, replyId)
        statement.setString(2, authorHandle)
      }
    ).headOption
  }

  private def bindTopic(statement: PreparedStatement, topic: ForumTopicRecord): Unit = {
    statement.setString(1, topic.threadId.value)
    statement.setString(2, topic.title)
    statement.setString(3, topic.body)
    statement.setString(4, topic.tag)
    statement.setString(5, topic.authorHandle)
    statement.setLong(6, topic.createdAt)
    statement.setLong(7, topic.updatedAt)
  }

  private def bindReply(statement: PreparedStatement, reply: ForumReplyRecord): Unit = {
    statement.setString(1, reply.replyId)
    statement.setString(2, reply.threadId.value)
    statement.setString(3, reply.authorHandle)
    statement.setString(4, reply.body)
    statement.setLong(5, reply.createdAt)
  }

  private def readTopics(resultSet: ResultSet): Seq[ForumTopicRecord] = {
    val records = Vector.newBuilder[ForumTopicRecord]
    while (resultSet.next()) {
      records += readTopic(resultSet)
    }
    records.result()
  }

  private def readReplies(resultSet: ResultSet): Seq[ForumReplyRecord] = {
    val records = Vector.newBuilder[ForumReplyRecord]
    while (resultSet.next()) {
      records += readReply(resultSet)
    }
    records.result()
  }

  private def readVotes(resultSet: ResultSet): Seq[ForumVoteRecord] = {
    val records = Vector.newBuilder[ForumVoteRecord]
    while (resultSet.next()) {
      records += readVote(resultSet)
    }
    records.result()
  }

  private def readReplyVotes(resultSet: ResultSet): Seq[ForumReplyVoteRecord] = {
    val records = Vector.newBuilder[ForumReplyVoteRecord]
    while (resultSet.next()) {
      records += readReplyVote(resultSet)
    }
    records.result()
  }

  private def readTopic(resultSet: ResultSet): ForumTopicRecord = {
    ForumTopicRecord(
      threadId = ThreadId(resultSet.getString("thread_id")),
      title = resultSet.getString("title"),
      body = resultSet.getString("body"),
      tag = resultSet.getString("tag"),
      authorHandle = resultSet.getString("author_handle"),
      createdAt = resultSet.getLong("created_at"),
      updatedAt = resultSet.getLong("updated_at")
    )
  }

  private def readReply(resultSet: ResultSet): ForumReplyRecord = {
    ForumReplyRecord(
      replyId = resultSet.getString("reply_id"),
      threadId = ThreadId(resultSet.getString("thread_id")),
      authorHandle = resultSet.getString("author_handle"),
      body = resultSet.getString("body"),
      createdAt = resultSet.getLong("created_at")
    )
  }

  private def readVote(resultSet: ResultSet): ForumVoteRecord = {
    ForumVoteRecord(
      threadId = ThreadId(resultSet.getString("thread_id")),
      authorHandle = resultSet.getString("author_handle"),
      vote = ForumVoteChoice.fromString(resultSet.getString("vote")).getOrElse(ForumVoteChoice.Up),
      updatedAt = resultSet.getLong("updated_at")
    )
  }

  private def readReplyVote(resultSet: ResultSet): ForumReplyVoteRecord = {
    ForumReplyVoteRecord(
      replyId = resultSet.getString("reply_id"),
      authorHandle = resultSet.getString("author_handle"),
      vote = ForumVoteChoice.fromString(resultSet.getString("vote")).getOrElse(ForumVoteChoice.Up),
      updatedAt = resultSet.getLong("updated_at")
    )
  }
}
