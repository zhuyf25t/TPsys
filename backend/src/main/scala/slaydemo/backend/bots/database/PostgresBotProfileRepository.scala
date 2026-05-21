package slaydemo.backend.bots.database

import slaydemo.backend.bots.objects.{
  BotProfileRecord,
  DemoBotProfiles
}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresBotProfileRepository(settings: PostgresConnectionSettings) extends BotProfileRepository {
  PostgresBotProfileSchema.initialize(settings)
  seedDefaultsIfEmpty()

  override def list(): Vector[BotProfileRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT bot_id, handle, display_name, initial_rating, profile_tone, strategy_label,
          |  avatar_key, texture_key, skin_label, profile_order
          |FROM bot_profiles
          |ORDER BY profile_order ASC, bot_id ASC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement)(PostgresBotProfileRecordMapper.readRecords)
      }
    }

  override def save(record: BotProfileRecord): BotProfileRecord = {
    PostgresSupport.withTransactionConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO bot_profiles (
          |  bot_id, handle, display_name, initial_rating, profile_tone, strategy_label,
          |  avatar_key, texture_key, skin_label, profile_order
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (bot_id) DO UPDATE SET
          |  handle = EXCLUDED.handle,
          |  display_name = EXCLUDED.display_name,
          |  initial_rating = EXCLUDED.initial_rating,
          |  profile_tone = EXCLUDED.profile_tone,
          |  strategy_label = EXCLUDED.strategy_label,
          |  avatar_key = EXCLUDED.avatar_key,
          |  texture_key = EXCLUDED.texture_key,
          |  skin_label = EXCLUDED.skin_label,
          |  profile_order = EXCLUDED.profile_order""".stripMargin
      ) { statement =>
        PostgresBotProfileRecordMapper.bindRecord(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }

  private def seedDefaultsIfEmpty(): Unit = {
    val isEmpty = PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, "SELECT 1 FROM bot_profiles LIMIT 1") { statement =>
        PostgresSupport.withResultSet(statement)(resultSet => !resultSet.next())
      }
    }

    if (isEmpty) {
      DemoBotProfiles.all.foreach(save)
    }
  }

}
