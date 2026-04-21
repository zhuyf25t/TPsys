package slaydemo.backend.battle.database

import java.sql.{PreparedStatement, ResultSet, Types}

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
          |  battle_id, handle, display_name, finished_at, finished_at_label,
          |  duration_ms, score, placement, alive_at_end, rating_before,
          |  rating_delta, rating_after, result_label, mode_label, map_label,
          |  highlight_line, players_line, timeline_hint, current_loadout
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (battle_id) DO UPDATE SET
          |  handle = EXCLUDED.handle,
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

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS battle_results (
          |  battle_id TEXT PRIMARY KEY,
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
        "CREATE INDEX IF NOT EXISTS battle_results_finished_at_idx ON battle_results (finished_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS battle_results_handle_finished_at_idx ON battle_results (lower(handle), finished_at DESC)"
      )(_.executeUpdate())
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
    statement.setString(1, record.battleId.value)
    statement.setString(2, record.handle.value)
    statement.setString(3, record.displayName)
    statement.setLong(4, record.finishedAt)
    statement.setString(5, record.finishedAtLabel)
    statement.setLong(6, record.durationMs)
    statement.setInt(7, record.score)
    record.placement match {
      case Some(value) => statement.setInt(8, value)
      case None        => statement.setNull(8, Types.INTEGER)
    }
    statement.setBoolean(9, record.aliveAtEnd)
    statement.setInt(10, record.ratingBefore)
    statement.setInt(11, record.ratingDelta)
    statement.setInt(12, record.ratingAfter)
    statement.setString(13, record.resultLabel)
    statement.setString(14, record.modeLabel)
    statement.setString(15, record.mapLabel)
    statement.setString(16, record.highlightLine)
    statement.setString(17, record.playersLine)
    statement.setString(18, record.timelineHint)
    record.currentLoadout match {
      case Some(value) => statement.setString(19, value)
      case None        => statement.setNull(19, Types.VARCHAR)
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
