package slaydemo.backend.bots.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.bots.objects.{BotProfileRecord, BotSkinProfile, DemoBotProfiles}
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}

final class PostgresBotProfileRepository(config: PostgresConfig) extends BotProfileRepository {
  initialize()
  seedDefaultsIfEmpty()

  override def list(): Seq[BotProfileRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT bot_id, handle, display_name, initial_rating, profile_tone, strategy_label,
          |       avatar_key, texture_key, skin_label, profile_order
          |FROM bot_profiles
          |ORDER BY profile_order ASC, bot_id ASC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement)(readRecords)
      }
    }
  }

  override def save(record: BotProfileRecord): BotProfileRecord = {
    PostgresSupport.withConnection(config) { connection =>
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
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }

    record
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS bot_profiles (
          |  bot_id TEXT PRIMARY KEY,
          |  handle TEXT NOT NULL,
          |  display_name TEXT NOT NULL,
          |  initial_rating INT NOT NULL,
          |  profile_tone TEXT NOT NULL,
          |  strategy_label TEXT NOT NULL,
          |  avatar_key TEXT NOT NULL,
          |  texture_key TEXT NOT NULL,
          |  skin_label TEXT NOT NULL,
          |  profile_order INT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS bot_profiles_profile_order_idx ON bot_profiles (profile_order ASC, bot_id ASC)"
      )(_.executeUpdate())
    }
  }

  private def seedDefaultsIfEmpty(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      val empty = PostgresSupport.withStatement(connection, "SELECT 1 FROM bot_profiles LIMIT 1") { statement =>
        PostgresSupport.withResultSet(statement)(resultSet => !resultSet.next())
      }

      if (empty) {
        DemoBotProfiles.all.foreach(save)
      }
    }
  }

  private def bindRecord(statement: PreparedStatement, record: BotProfileRecord): Unit = {
    statement.setString(1, record.botId)
    statement.setString(2, record.handle)
    statement.setString(3, record.displayName)
    statement.setInt(4, record.initialRating)
    statement.setString(5, record.profileTone)
    statement.setString(6, record.strategyLabel)
    statement.setString(7, record.skin.avatarKey)
    statement.setString(8, record.skin.textureKey)
    statement.setString(9, record.skin.label)
    statement.setInt(10, record.profileOrder)
  }

  private def readRecords(resultSet: ResultSet): Seq[BotProfileRecord] = {
    val buffer = scala.collection.mutable.ArrayBuffer.empty[BotProfileRecord]
    while (resultSet.next()) {
      buffer += readRecord(resultSet)
    }
    buffer.toSeq
  }

  private def readRecord(resultSet: ResultSet): BotProfileRecord = {
    BotProfileRecord(
      botId = resultSet.getString("bot_id"),
      handle = resultSet.getString("handle"),
      displayName = resultSet.getString("display_name"),
      initialRating = resultSet.getInt("initial_rating"),
      profileTone = resultSet.getString("profile_tone"),
      strategyLabel = resultSet.getString("strategy_label"),
      skin = BotSkinProfile(
        avatarKey = resultSet.getString("avatar_key"),
        textureKey = resultSet.getString("texture_key"),
        label = resultSet.getString("skin_label")
      ),
      profileOrder = resultSet.getInt("profile_order")
    )
  }
}
