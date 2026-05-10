package slaydemo.backend.bots.database

import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

private[database] object PostgresBotProfileSchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS bot_profiles (
          |  bot_id TEXT PRIMARY KEY,
          |  handle TEXT NOT NULL,
          |  display_name TEXT NOT NULL,
          |  initial_rating INTEGER NOT NULL,
          |  profile_tone TEXT NOT NULL,
          |  strategy_label TEXT NOT NULL,
          |  avatar_key TEXT NOT NULL,
          |  texture_key TEXT NOT NULL,
          |  skin_label TEXT NOT NULL,
          |  profile_order INTEGER NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS bot_profiles_profile_order_idx ON bot_profiles (profile_order ASC, bot_id ASC)"
      )(_.executeUpdate())
    }
}
