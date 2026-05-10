package slaydemo.backend.forum.database

import java.sql.{Connection, PreparedStatement, ResultSet}

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

private[database] object PostgresForumRecordReader {
  def readTopicById(connection: Connection, topicId: ForumTopicId): Option[ForumTopicRecord] =
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
