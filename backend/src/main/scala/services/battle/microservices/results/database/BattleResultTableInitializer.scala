package services.battle.microservices.results.database

import java.sql.Connection

import cats.effect.IO

import system.database.PostgresSupport

object BattleResultTableInitializer {
  def initialize(connection: Connection): IO[Unit] = IO.blocking {
    PostgresSupport.withStatement(
      connection,
      """CREATE TABLE IF NOT EXISTS battle_results (
        |  result_id TEXT PRIMARY KEY,
        |  battle_id TEXT NOT NULL,
        |  handle TEXT NOT NULL,
        |  display_name TEXT NOT NULL,
        |  finished_at BIGINT NOT NULL,
        |  finished_at_label TEXT NOT NULL,
        |  duration_ms BIGINT NOT NULL,
        |  score INTEGER NOT NULL,
        |  placement INTEGER NULL,
        |  alive_at_end BOOLEAN NOT NULL,
        |  rating_before INTEGER NOT NULL,
        |  rating_delta INTEGER NOT NULL,
        |  rating_after INTEGER NOT NULL,
        |  result_label TEXT NOT NULL,
        |  mode_label TEXT NOT NULL,
        |  map_label TEXT NOT NULL,
        |  highlight_line TEXT NOT NULL,
        |  players_line TEXT NOT NULL,
        |  timeline_hint TEXT NOT NULL,
        |  current_loadout TEXT NULL
        |)""".stripMargin
    )(_.executeUpdate())

    requiredColumns.foreach(addColumnIfMissing(connection, _))
    backfillResultIds(connection)
    ensureResultIdPrimaryKey(connection)
    ensureIndexes(connection)
  }

  private def requiredColumns: Vector[String] =
    Vector(
      "result_id TEXT",
      "battle_id TEXT NOT NULL DEFAULT ''",
      "handle TEXT NOT NULL DEFAULT ''",
      "display_name TEXT NOT NULL DEFAULT ''",
      "finished_at BIGINT NOT NULL DEFAULT 0",
      "finished_at_label TEXT NOT NULL DEFAULT ''",
      "duration_ms BIGINT NOT NULL DEFAULT 0",
      "score INTEGER NOT NULL DEFAULT 0",
      "placement INTEGER",
      "alive_at_end BOOLEAN NOT NULL DEFAULT FALSE",
      "rating_before INTEGER NOT NULL DEFAULT 0",
      "rating_delta INTEGER NOT NULL DEFAULT 0",
      "rating_after INTEGER NOT NULL DEFAULT 0",
      "result_label TEXT NOT NULL DEFAULT ''",
      "mode_label TEXT NOT NULL DEFAULT ''",
      "map_label TEXT NOT NULL DEFAULT ''",
      "highlight_line TEXT NOT NULL DEFAULT ''",
      "players_line TEXT NOT NULL DEFAULT ''",
      "timeline_hint TEXT NOT NULL DEFAULT ''",
      "current_loadout TEXT"
    )

  private def addColumnIfMissing(connection: Connection, columnDefinition: String): Unit =
    PostgresSupport.withStatement(
      connection,
      s"ALTER TABLE battle_results ADD COLUMN IF NOT EXISTS $columnDefinition"
    )(_.executeUpdate())

  private def backfillResultIds(connection: Connection): Unit = {
    PostgresSupport.withStatement(
      connection,
      "UPDATE battle_results SET result_id = battle_id || ':' || lower(trim(handle)) WHERE result_id IS NULL OR result_id = ''"
    )(_.executeUpdate())

    PostgresSupport.withStatement(
      connection,
      "ALTER TABLE battle_results ALTER COLUMN result_id SET NOT NULL"
    )(_.executeUpdate())
  }

  private def ensureResultIdPrimaryKey(connection: Connection): Unit =
    PostgresSupport.withStatement(
      connection,
      """DO $$
        |DECLARE
        |  primary_key_name TEXT;
        |  primary_key_columns TEXT[];
        |BEGIN
        |  SELECT constraint_info.conname, constraint_info.columns
        |  INTO primary_key_name, primary_key_columns
        |  FROM (
        |    SELECT constraint_record.conname, array_agg(attribute.attname::TEXT ORDER BY key_column.ordinality) AS columns
        |    FROM pg_constraint constraint_record
        |    JOIN unnest(constraint_record.conkey) WITH ORDINALITY AS key_column(attnum, ordinality) ON TRUE
        |    JOIN pg_attribute attribute
        |      ON attribute.attrelid = constraint_record.conrelid
        |      AND attribute.attnum = key_column.attnum
        |    WHERE constraint_record.conrelid = 'battle_results'::regclass
        |      AND constraint_record.contype = 'p'
        |    GROUP BY constraint_record.conname
        |  ) constraint_info;
        |
        |  IF primary_key_name IS NOT NULL AND primary_key_columns IS DISTINCT FROM ARRAY['result_id'] THEN
        |    EXECUTE format('ALTER TABLE battle_results DROP CONSTRAINT %I', primary_key_name);
        |    primary_key_name := NULL;
        |  END IF;
        |
        |  IF primary_key_name IS NULL THEN
        |    ALTER TABLE battle_results ADD CONSTRAINT battle_results_pkey PRIMARY KEY (result_id);
        |  END IF;
        |END $$""".stripMargin
    )(_.executeUpdate())

  private def ensureIndexes(connection: Connection): Unit = {
    PostgresSupport.withStatement(
      connection,
      "CREATE UNIQUE INDEX IF NOT EXISTS battle_results_result_id_unique_idx ON battle_results (result_id)"
    )(_.executeUpdate())

    PostgresSupport.withStatement(
      connection,
      "CREATE INDEX IF NOT EXISTS battle_results_finished_at_idx ON battle_results (finished_at DESC)"
    )(_.executeUpdate())

    PostgresSupport.withStatement(
      connection,
      "CREATE INDEX IF NOT EXISTS battle_results_handle_finished_at_idx ON battle_results (lower(handle), finished_at DESC)"
    )(_.executeUpdate())

    PostgresSupport.withStatement(
      connection,
      "CREATE INDEX IF NOT EXISTS battle_results_battle_id_finished_at_idx ON battle_results (battle_id, finished_at DESC)"
    )(_.executeUpdate())
  }
}
