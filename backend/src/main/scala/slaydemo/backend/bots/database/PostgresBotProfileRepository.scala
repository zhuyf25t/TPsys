package slaydemo.backend.bots.database

import java.sql.{PreparedStatement, ResultSet}
import java.util.Locale

import slaydemo.backend.bots.objects.{
  BotAvatarKey,
  BotId,
  BotInitialRating,
  BotProfileOrder,
  BotProfileRecord,
  BotProfileTone,
  BotSkinLabel,
  BotSkinProfile,
  BotStrategyLabel,
  BotTextureKey,
  DemoBotProfiles
}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresBotProfileRepository(settings: PostgresConnectionSettings) extends BotProfileRepository {
  initialize()
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
        PostgresSupport.withResultSet(statement)(readRecords)
      }
    }

  override def save(record: BotProfileRecord): BotProfileRecord = {
    PostgresSupport.withConnection(settings) { connection =>
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

  private def initialize(): Unit =
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

  private def bindRecord(statement: PreparedStatement, record: BotProfileRecord): Unit = {
    statement.setString(1, record.botId.value)
    statement.setString(2, record.handle.value)
    statement.setString(3, record.displayName.value)
    statement.setInt(4, record.initialRating.value)
    statement.setString(5, BotProfileTone.wireValue(record.profileTone))
    statement.setString(6, record.strategyLabel.value)
    statement.setString(7, record.skin.avatarKey.value)
    statement.setString(8, record.skin.textureKey.value)
    statement.setString(9, record.skin.label.value)
    statement.setInt(10, record.profileOrder.value)
  }

  private def readRecords(resultSet: ResultSet): Vector[BotProfileRecord] = {
    val records = Vector.newBuilder[BotProfileRecord]
    while (resultSet.next()) {
      records += readRecord(resultSet)
    }
    records.result()
  }

  private def readRecord(resultSet: ResultSet): BotProfileRecord =
    BotProfileRecord(
      botId = BotId(resultSet.getString("bot_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      initialRating = BotInitialRating(resultSet.getInt("initial_rating")),
      profileTone = readTone(resultSet.getString("profile_tone")),
      strategyLabel = BotStrategyLabel(resultSet.getString("strategy_label")),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey(resultSet.getString("avatar_key")),
        textureKey = BotTextureKey(resultSet.getString("texture_key")),
        label = BotSkinLabel(resultSet.getString("skin_label"))
      ),
      profileOrder = BotProfileOrder(resultSet.getInt("profile_order"))
    )

  private def readTone(value: String): BotProfileTone =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("") match {
      case "scrappy"     => BotProfileTone.Scrappy
      case "aggressive"  => BotProfileTone.Aggressive
      case "patient"     => BotProfileTone.Patient
      case "opportunist" => BotProfileTone.Opportunist
      case _             => BotProfileTone.Steady
    }
}
