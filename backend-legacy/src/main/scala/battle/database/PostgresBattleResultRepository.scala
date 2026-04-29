package slaydemo.backend.battle.database

import java.sql.{Connection, PreparedStatement, ResultSet, Types}

import slaydemo.backend.battle.objects.BattleResultRecord
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}
import slaydemo.backend.shared.objects.{BattleId, UserId}

final class PostgresBattleResultRepository(config: PostgresConfig) extends BattleResultRepository {
  initialize()

  override def save(record: BattleResultRecord): BattleResultRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO battle_results (
          |  result_id, battle_id, handle, display_name, finished_at, finished_at_label,
          |  duration_ms, score, placement, alive_at_end, rating_before,
          |  rating_delta, rating_after, result_label, mode_label, map_label,
          |  highlight_line, players_line, timeline_hint, current_loadout
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (result_id) DO UPDATE SET
          |  handle = EXCLUDED.handle,
          |  battle_id = EXCLUDED.battle_id,
          |  display_name = EXCLUDED.display_name,
          |  finished_at = EXCLUDED.finished_at,
          |  finished_at_label = EXCLUDED.finished_at_label,
          |  duration_ms = EXCLUDED.duration_ms,
          |  score = EXCLUDED.score,
          |  placement = EXCLUDED.placement,
          |  alive_at_end = EXCLUDED.alive_at_end,
          |  rating_before = EXCLUDED.rating_before,
          |  rating_delta = EXCLUDED.rating_delta,
          |  rating_after = EXCLUDED.rating_after,
          |  result_label = EXCLUDED.result_label,
          |  mode_label = EXCLUDED.mode_label,
          |  map_label = EXCLUDED.map_label,
          |  highlight_line = EXCLUDED.highlight_line,
          |  players_line = EXCLUDED.players_line,
          |  timeline_hint = EXCLUDED.timeline_hint,
          |  current_loadout = EXCLUDED.current_loadout""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }

    record
  }

  override def list(limit: Int): Seq[BattleResultRecord] = {
    queryMany(
      """SELECT battle_id, handle, display_name, finished_at, finished_at_label,
        |  duration_ms, score, placement, alive_at_end, rating_before,
        |  rating_delta, rating_after, result_label, mode_label, map_label,
        |  highlight_line, players_line, timeline_hint, current_loadout
        |FROM battle_results
        |ORDER BY finished_at DESC
        |LIMIT ?""".stripMargin,
      statement => statement.setInt(1, limit.max(0))
    )
  }

  override def listByHandle(handle: String, limit: Int): Seq[BattleResultRecord] = {
    queryMany(
      """SELECT battle_id, handle, display_name, finished_at, finished_at_label,
        |  duration_ms, score, placement, alive_at_end, rating_before,
        |  rating_delta, rating_after, result_label, mode_label, map_label,
        |  highlight_line, players_line, timeline_hint, current_loadout
        |FROM battle_results
        |WHERE lower(handle) = lower(?)
        |ORDER BY finished_at DESC
        |LIMIT ?""".stripMargin,
      statement => {
        statement.setString(1, handle.trim)
        statement.setInt(2, limit.max(0))
      }
    )
  }

  override def listByBattleId(battleId: String, limit: Int): Seq[BattleResultRecord] = {
    queryMany(
      """SELECT battle_id, handle, display_name, finished_at, finished_at_label,
        |  duration_ms, score, placement, alive_at_end, rating_before,
        |  rating_delta, rating_after, result_label, mode_label, map_label,
        |  highlight_line, players_line, timeline_hint, current_loadout
        |FROM battle_results
        |WHERE lower(battle_id) = lower(?)
        |ORDER BY finished_at DESC, placement ASC NULLS LAST
        |LIMIT ?""".stripMargin,
      statement => {
        statement.setString(1, battleId.trim)
        statement.setInt(2, limit.max(0))
      }
    )
  }

  override def listByHandleAndBattleId(handle: String, battleId: String, limit: Int): Seq[BattleResultRecord] = {
    queryMany(
      """SELECT battle_id, handle, display_name, finished_at, finished_at_label,
        |  duration_ms, score, placement, alive_at_end, rating_before,
        |  rating_delta, rating_after, result_label, mode_label, map_label,
        |  highlight_line, players_line, timeline_hint, current_loadout
        |FROM battle_results
        |WHERE lower(handle) = lower(?) AND lower(battle_id) = lower(?)
        |ORDER BY finished_at DESC, placement ASC NULLS LAST
        |LIMIT ?""".stripMargin,
      statement => {
        statement.setString(1, handle.trim)
        statement.setString(2, battleId.trim)
        statement.setInt(3, limit.max(0))
      }
    )
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
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

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE battle_results ADD COLUMN IF NOT EXISTS result_id TEXT"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "UPDATE battle_results SET result_id = battle_id || ':' || lower(trim(handle)) WHERE result_id IS NULL OR result_id = ''"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE battle_results ALTER COLUMN result_id SET NOT NULL"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """DO $$
          |DECLARE
          |  primary_key_name text;
          |  primary_key_columns text[];
          |BEGIN
          |  SELECT constraint_info.conname, constraint_info.columns
          |  INTO primary_key_name, primary_key_columns
          |  FROM (
          |    SELECT constraint_record.conname, array_agg(attribute.attname::text ORDER BY key_column.ordinality) AS columns
          |    FROM pg_constraint constraint_record
          |    JOIN unnest(constraint_record.conkey) WITH ORDINALITY AS key_column(attnum, ordinality) ON true
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

      enforceLogicalResultUniqueness(connection)

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
        "CREATE INDEX IF NOT EXISTS battle_results_battle_id_finished_at_idx ON battle_results (lower(battle_id), finished_at DESC)"
      )(_.executeUpdate())
    }
  }

  private def enforceLogicalResultUniqueness(connection: Connection): Unit = {
    val previousAutoCommit = connection.getAutoCommit
    connection.setAutoCommit(false)

    try {
      PostgresSupport.withStatement(
        connection,
        "LOCK TABLE battle_results IN SHARE ROW EXCLUSIVE MODE"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        """DELETE FROM battle_results victim
          |USING (
          |  SELECT result_id
          |  FROM (
          |    SELECT
          |      result_id,
          |      row_number() OVER (
          |        PARTITION BY lower(trim(battle_id)), lower(trim(handle))
          |        ORDER BY finished_at DESC, result_id ASC
          |      ) AS duplicate_rank
          |    FROM battle_results
          |  ) ranked
          |  WHERE duplicate_rank > 1
          |) duplicate
          |WHERE victim.result_id = duplicate.result_id""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE UNIQUE INDEX IF NOT EXISTS battle_results_logical_key_unique_idx ON battle_results (lower(trim(battle_id)), lower(trim(handle)))"
      )(_.executeUpdate())

      connection.commit()
    } catch {
      case error: Throwable =>
        connection.rollback()
        throw error
    } finally {
      connection.setAutoCommit(previousAutoCommit)
    }
  }

  private def queryMany(sql: String, bind: PreparedStatement => Unit): Seq[BattleResultRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val records = Vector.newBuilder[BattleResultRecord]
          while (resultSet.next()) {
            records += readRecord(resultSet)
          }
          records.result()
        }
      }
    }
  }

  private def bindRecord(statement: PreparedStatement, record: BattleResultRecord): Unit = {
    statement.setString(1, record.resultId)
    statement.setString(2, record.battleId.value)
    statement.setString(3, record.handle.value)
    statement.setString(4, record.displayName)
    statement.setLong(5, record.finishedAt)
    statement.setString(6, record.finishedAtLabel)
    statement.setLong(7, record.durationMs)
    statement.setInt(8, record.score)
    record.placement match {
      case Some(value) => statement.setInt(9, value)
      case None        => statement.setNull(9, Types.INTEGER)
    }
    statement.setBoolean(10, record.aliveAtEnd)
    statement.setInt(11, record.ratingBefore)
    statement.setInt(12, record.ratingDelta)
    statement.setInt(13, record.ratingAfter)
    statement.setString(14, record.resultLabel)
    statement.setString(15, record.modeLabel)
    statement.setString(16, record.mapLabel)
    statement.setString(17, record.highlightLine)
    statement.setString(18, record.playersLine)
    statement.setString(19, record.timelineHint)
    record.currentLoadout match {
      case Some(value) => statement.setString(20, value)
      case None        => statement.setNull(20, Types.VARCHAR)
    }
  }

  private def readRecord(resultSet: ResultSet): BattleResultRecord = {
    val placement = resultSet.getInt("placement")
    BattleResultRecord(
      battleId = BattleId(resultSet.getString("battle_id")),
      handle = UserId(resultSet.getString("handle")),
      displayName = resultSet.getString("display_name"),
      finishedAt = resultSet.getLong("finished_at"),
      finishedAtLabel = resultSet.getString("finished_at_label"),
      durationMs = resultSet.getLong("duration_ms"),
      score = resultSet.getInt("score"),
      placement = if (resultSet.wasNull()) None else Some(placement),
      aliveAtEnd = resultSet.getBoolean("alive_at_end"),
      ratingBefore = resultSet.getInt("rating_before"),
      ratingDelta = resultSet.getInt("rating_delta"),
      ratingAfter = resultSet.getInt("rating_after"),
      resultLabel = resultSet.getString("result_label"),
      modeLabel = resultSet.getString("mode_label"),
      mapLabel = resultSet.getString("map_label"),
      highlightLine = resultSet.getString("highlight_line"),
      playersLine = resultSet.getString("players_line"),
      timelineHint = resultSet.getString("timeline_hint"),
      currentLoadout = Option(resultSet.getString("current_loadout"))
    )
  }
}
