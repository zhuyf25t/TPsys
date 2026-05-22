package services.replay.database

import java.sql.Connection

import services.replay.database.PostgresReplayRecordMapper.readComments
import services.replay.objects.{ReplayCommentRecord, ReplayId}
import system.database.PostgresSupport

private[database] object PostgresReplayCommentQueries {
  def saveComment(connection: Connection, record: ReplayCommentRecord): Unit =
    PostgresSupport.withStatement(
      connection,
      """INSERT INTO replay_comments (
        |  comment_id, replay_id, author_handle, body, created_at
        |) VALUES (?, ?, ?, ?, ?)
        |ON CONFLICT (comment_id) DO UPDATE SET
        |  replay_id = EXCLUDED.replay_id,
        |  author_handle = EXCLUDED.author_handle,
        |  body = EXCLUDED.body,
        |  created_at = EXCLUDED.created_at""".stripMargin
    ) { statement =>
      statement.setString(1, record.id.value)
      statement.setString(2, record.replayId.value)
      statement.setString(3, record.authorHandle.value)
      statement.setString(4, record.body)
      statement.setLong(5, record.createdAt.value)
      statement.executeUpdate()
    }

  def listComments(connection: Connection, replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
    PostgresSupport.withStatement(
      connection,
      """SELECT comment_id, replay_id, author_handle, body, created_at
        |FROM (
        |  SELECT comment_id, replay_id, author_handle, body, created_at
        |  FROM replay_comments
        |  WHERE replay_id = ?
        |  ORDER BY created_at DESC, comment_id DESC
        |  LIMIT ?
        |) recent
        |ORDER BY created_at ASC, comment_id ASC""".stripMargin
    ) { statement =>
      statement.setString(1, replayId.value)
      statement.setInt(2, math.max(0, limit))
      PostgresSupport.withResultSet(statement)(readComments)
    }
}
