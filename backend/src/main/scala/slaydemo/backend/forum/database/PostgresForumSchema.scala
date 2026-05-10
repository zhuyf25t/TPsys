package slaydemo.backend.forum.database

import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

private[database] object PostgresForumSchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
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
}
