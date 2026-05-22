package services.replay.database

import java.sql.Connection

import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

private[database] object PostgresReplaySchema {
  def initialize(settings: PostgresConnectionSettings): Unit =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS replay_records (
          |  replay_id TEXT PRIMARY KEY,
          |  battle_id TEXT NOT NULL,
          |  handle TEXT NOT NULL,
          |  display_name TEXT NOT NULL,
          |  finished_at BIGINT NOT NULL,
          |  finished_at_label TEXT NOT NULL,
          |  title TEXT NOT NULL,
          |  mode_label TEXT NOT NULL,
          |  result_label TEXT NOT NULL,
          |  map_label TEXT NOT NULL,
          |  highlight_line TEXT NOT NULL,
          |  cover_label TEXT NOT NULL,
          |  players_line TEXT NOT NULL,
          |  timeline_hint TEXT NOT NULL,
          |  score INTEGER NOT NULL,
          |  placement INTEGER NULL,
          |  duration_ms BIGINT NOT NULL,
          |  alive_at_end BOOLEAN NOT NULL,
          |  thumbnail_data_url TEXT NULL,
          |  current_loadout TEXT NULL,
          |  frame_count INTEGER NOT NULL,
          |  playback_available BOOLEAN NOT NULL,
          |  frames_json_b64 TEXT NOT NULL,
          |  rating_before INTEGER NULL,
          |  rating_delta INTEGER NULL,
          |  rating_after INTEGER NULL
          |)""".stripMargin
      )(_.executeUpdate())

      replayRecordColumns.foreach(addColumnIfMissing(connection, "replay_records", _))

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS replay_records_finished_at_idx ON replay_records (finished_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS replay_comments (
          |  comment_id TEXT PRIMARY KEY,
          |  replay_id TEXT NOT NULL,
          |  author_handle TEXT NOT NULL,
          |  body TEXT NOT NULL,
          |  created_at BIGINT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      replayCommentColumns.foreach(addColumnIfMissing(connection, "replay_comments", _))

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS replay_comments_replay_id_created_at_idx ON replay_comments (replay_id, created_at ASC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS replay_settlements (
          |  replay_id TEXT NOT NULL,
          |  handle TEXT NOT NULL,
          |  display_name TEXT NOT NULL,
          |  result_label TEXT NOT NULL,
          |  highlight_line TEXT NOT NULL,
          |  score INTEGER NOT NULL,
          |  placement INTEGER NULL,
          |  rating_before INTEGER NULL,
          |  rating_delta INTEGER NULL,
          |  rating_after INTEGER NULL,
          |  alive_at_end BOOLEAN NOT NULL,
          |  current_loadout TEXT NULL,
          |  PRIMARY KEY (replay_id, handle)
          |)""".stripMargin
      )(_.executeUpdate())

      replaySettlementColumns.foreach(addColumnIfMissing(connection, "replay_settlements", _))

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS replay_settlements_replay_id_idx ON replay_settlements (replay_id)"
      )(_.executeUpdate())
    }

  private def replayRecordColumns: Vector[String] =
    Vector(
      "replay_id TEXT NOT NULL DEFAULT ''",
      "battle_id TEXT NOT NULL DEFAULT ''",
      "handle TEXT NOT NULL DEFAULT ''",
      "display_name TEXT NOT NULL DEFAULT ''",
      "finished_at BIGINT NOT NULL DEFAULT 0",
      "finished_at_label TEXT NOT NULL DEFAULT ''",
      "title TEXT NOT NULL DEFAULT ''",
      "mode_label TEXT NOT NULL DEFAULT ''",
      "result_label TEXT NOT NULL DEFAULT ''",
      "map_label TEXT NOT NULL DEFAULT ''",
      "highlight_line TEXT NOT NULL DEFAULT ''",
      "cover_label TEXT NOT NULL DEFAULT ''",
      "players_line TEXT NOT NULL DEFAULT ''",
      "timeline_hint TEXT NOT NULL DEFAULT ''",
      "score INTEGER NOT NULL DEFAULT 0",
      "placement INTEGER",
      "duration_ms BIGINT NOT NULL DEFAULT 0",
      "alive_at_end BOOLEAN NOT NULL DEFAULT FALSE",
      "thumbnail_data_url TEXT",
      "current_loadout TEXT",
      "frame_count INTEGER NOT NULL DEFAULT 0",
      "playback_available BOOLEAN NOT NULL DEFAULT FALSE",
      "frames_json_b64 TEXT NOT NULL DEFAULT 'W10='",
      "rating_before INTEGER",
      "rating_delta INTEGER",
      "rating_after INTEGER"
    )

  private def replayCommentColumns: Vector[String] =
    Vector(
      "comment_id TEXT NOT NULL DEFAULT ''",
      "replay_id TEXT NOT NULL DEFAULT ''",
      "author_handle TEXT NOT NULL DEFAULT ''",
      "body TEXT NOT NULL DEFAULT ''",
      "created_at BIGINT NOT NULL DEFAULT 0"
    )

  private def replaySettlementColumns: Vector[String] =
    Vector(
      "replay_id TEXT NOT NULL DEFAULT ''",
      "handle TEXT NOT NULL DEFAULT ''",
      "display_name TEXT NOT NULL DEFAULT ''",
      "result_label TEXT NOT NULL DEFAULT ''",
      "highlight_line TEXT NOT NULL DEFAULT ''",
      "score INTEGER NOT NULL DEFAULT 0",
      "placement INTEGER",
      "rating_before INTEGER",
      "rating_delta INTEGER",
      "rating_after INTEGER",
      "alive_at_end BOOLEAN NOT NULL DEFAULT FALSE",
      "current_loadout TEXT"
    )

  private def addColumnIfMissing(connection: Connection, tableName: String, columnDefinition: String): Unit =
    PostgresSupport.withStatement(
      connection,
      s"ALTER TABLE $tableName ADD COLUMN IF NOT EXISTS $columnDefinition"
    )(_.executeUpdate())
}
