package slaydemo.backend.identity.database

import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

private[database] object PostgresIdentityAccountSchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS identity_accounts (
          |  user_id TEXT PRIMARY KEY,
          |  handle TEXT NOT NULL UNIQUE,
          |  display_name TEXT NOT NULL,
          |  skin_id TEXT NOT NULL,
          |  session_token TEXT NOT NULL,
          |  active BOOLEAN NOT NULL,
          |  password TEXT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE UNIQUE INDEX IF NOT EXISTS identity_accounts_handle_lower_idx ON identity_accounts (lower(handle))"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS identity_accounts_session_token_idx ON identity_accounts (session_token)"
      )(_.executeUpdate())
    }
}
