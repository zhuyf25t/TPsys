package slaydemo.backend.forum.database

import java.sql.Connection

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.objects.{ForumReplyId, ForumReplyRecord, ForumTopicId, ForumTopicRecord}
import slaydemo.backend.shared.database.PostgresSupport

private[database] object PostgresForumTopicQueries {
  def upsertTopic(connection: Connection, topic: ForumTopicRecord): Unit =
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

  def upsertReply(connection: Connection, topicId: ForumTopicId, reply: ForumReplyRecord): Unit =
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

  def topicExists(connection: Connection, topicId: ForumTopicId): Boolean =
    PostgresSupport.withStatement(connection, "SELECT 1 FROM forum_topics WHERE thread_id = ? LIMIT 1") { statement =>
      statement.setString(1, topicId.value)
      PostgresSupport.withResultSet(statement)(_.next())
    }

  def replyExists(connection: Connection, topicId: ForumTopicId, replyId: ForumReplyId): Boolean =
    PostgresSupport.withStatement(
      connection,
      "SELECT 1 FROM forum_replies WHERE thread_id = ? AND reply_id = ? LIMIT 1"
    ) { statement =>
      statement.setString(1, topicId.value)
      statement.setString(2, replyId.value)
      PostgresSupport.withResultSet(statement)(_.next())
    }

  def updateTopicTimestamp(connection: Connection, topicId: ForumTopicId, updatedAt: EpochMillis): Unit =
    PostgresSupport.withStatement(connection, "UPDATE forum_topics SET updated_at = ? WHERE thread_id = ?") { statement =>
      statement.setLong(1, updatedAt.value)
      statement.setString(2, topicId.value)
      statement.executeUpdate()
    }
}
