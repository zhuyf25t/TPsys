package services.forum.database

import java.sql.Connection

import services.battle.objects.EpochMillis
import services.forum.objects.{ForumReplyId, ForumTopicId, ForumVoteChoice, ForumVoterKey}
import services.identity.objects.PlayerHandle
import system.database.PostgresSupport

private[database] object PostgresForumVoteQueries {
  def writeTopicVote(
    connection: Connection,
    topicId: ForumTopicId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Unit =
    vote match {
      case Some(choice) =>
        PostgresSupport.withStatement(
          connection,
          """INSERT INTO forum_votes (thread_id, author_handle, vote, updated_at)
            |VALUES (?, ?, ?, ?)
            |ON CONFLICT (thread_id, author_handle) DO UPDATE SET
            |  vote = EXCLUDED.vote,
            |  updated_at = EXCLUDED.updated_at""".stripMargin
        ) { statement =>
          statement.setString(1, topicId.value)
          statement.setString(2, ForumVoterKey.fromHandle(authorHandle).value)
          statement.setString(3, ForumVoteChoice.wireValue(choice))
          statement.setLong(4, updatedAt.value)
          statement.executeUpdate()
        }
      case None =>
        PostgresSupport.withStatement(
          connection,
          "DELETE FROM forum_votes WHERE thread_id = ? AND author_handle = ?"
        ) { statement =>
          statement.setString(1, topicId.value)
          statement.setString(2, ForumVoterKey.fromHandle(authorHandle).value)
          statement.executeUpdate()
        }
    }

  def writeReplyVote(
    connection: Connection,
    replyId: ForumReplyId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Unit =
    vote match {
      case Some(choice) =>
        PostgresSupport.withStatement(
          connection,
          """INSERT INTO forum_reply_votes (reply_id, author_handle, vote, updated_at)
            |VALUES (?, ?, ?, ?)
            |ON CONFLICT (reply_id, author_handle) DO UPDATE SET
            |  vote = EXCLUDED.vote,
            |  updated_at = EXCLUDED.updated_at""".stripMargin
        ) { statement =>
          statement.setString(1, replyId.value)
          statement.setString(2, ForumVoterKey.fromHandle(authorHandle).value)
          statement.setString(3, ForumVoteChoice.wireValue(choice))
          statement.setLong(4, updatedAt.value)
          statement.executeUpdate()
        }
      case None =>
        PostgresSupport.withStatement(
          connection,
          "DELETE FROM forum_reply_votes WHERE reply_id = ? AND author_handle = ?"
        ) { statement =>
          statement.setString(1, replyId.value)
          statement.setString(2, ForumVoterKey.fromHandle(authorHandle).value)
          statement.executeUpdate()
        }
    }
}
