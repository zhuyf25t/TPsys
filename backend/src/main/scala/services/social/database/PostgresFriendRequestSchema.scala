package services.social.database

import java.sql.Connection

import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

private[database] object PostgresFriendRequestSchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS social_friend_requests (
          |  id TEXT PRIMARY KEY,
          |  source_handle TEXT NOT NULL,
          |  target_handle TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  status TEXT NOT NULL DEFAULT 'pending',
          |  responded_at BIGINT
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE social_friend_requests ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'pending'"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE social_friend_requests ADD COLUMN IF NOT EXISTS responded_at BIGINT"
      )(_.executeUpdate())

      if hasDuplicateRequestPairs(connection) then
        PostgresSupport.withStatement(
          connection,
          "CREATE INDEX IF NOT EXISTS social_friend_requests_source_target_lookup_idx ON social_friend_requests (lower(source_handle), lower(target_handle))"
        )(_.executeUpdate())
      else
        PostgresSupport.withStatement(
          connection,
          "CREATE UNIQUE INDEX IF NOT EXISTS social_friend_requests_source_target_unique_idx ON social_friend_requests (lower(source_handle), lower(target_handle))"
        )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS social_friend_requests_owner_created_at_idx ON social_friend_requests (lower(source_handle), lower(target_handle), created_at DESC)"
      )(_.executeUpdate())
    }

  private def hasDuplicateRequestPairs(connection: Connection): Boolean =
    PostgresSupport.withStatement(
      connection,
      """SELECT 1
        |FROM social_friend_requests
        |GROUP BY lower(source_handle), lower(target_handle)
        |HAVING count(*) > 1
        |LIMIT 1""".stripMargin
    ) { statement =>
      PostgresSupport.withResultSet(statement)(_.next())
    }
}
