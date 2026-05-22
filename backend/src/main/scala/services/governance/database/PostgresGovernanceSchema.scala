package services.governance.database

import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

private[database] object PostgresGovernanceSchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS governance_contribution_adjustments (
          |  id TEXT PRIMARY KEY,
          |  actor_handle TEXT NOT NULL,
          |  target_handle TEXT NOT NULL,
          |  delta INTEGER NOT NULL,
          |  reason TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  source_label TEXT NOT NULL DEFAULT '',
          |  source_path TEXT NOT NULL DEFAULT ''
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE governance_contribution_adjustments ADD COLUMN IF NOT EXISTS source_label TEXT NOT NULL DEFAULT ''"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE governance_contribution_adjustments ADD COLUMN IF NOT EXISTS source_path TEXT NOT NULL DEFAULT ''"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS governance_review_notifications (
          |  id TEXT PRIMARY KEY,
          |  actor_handle TEXT NOT NULL,
          |  kind TEXT NOT NULL,
          |  target_type TEXT NOT NULL,
          |  target_id TEXT NOT NULL,
          |  target_title TEXT NOT NULL,
          |  target_path TEXT NOT NULL,
          |  body TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  mail_id TEXT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_contribution_adjustments_created_at_idx ON governance_contribution_adjustments (created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_created_at_idx ON governance_review_notifications (created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_kind_created_at_idx ON governance_review_notifications (kind, created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_target_type_created_at_idx ON governance_review_notifications (target_type, created_at DESC)"
      )(_.executeUpdate())
    }
}
