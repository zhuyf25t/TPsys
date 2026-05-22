package services.battle.persistence

import java.sql.PreparedStatement

import services.battle.objects.{BattleId, BattleResultRecord}
import services.identity.objects.PlayerHandle
import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

final class PostgresBattleResultRepository(settings: PostgresConnectionSettings) extends BattleResultRepository {
  PostgresBattleResultSchema.initialize(settings)

  override def save(record: BattleResultRecord): BattleResultRecord = {
    PostgresSupport.withTransactionConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO battle_results (
          |  result_id, battle_id, handle, display_name, finished_at, finished_at_label,
          |  duration_ms, score, placement, alive_at_end, rating_before,
          |  rating_delta, rating_after, result_label, mode_label, map_label,
          |  highlight_line, players_line, timeline_hint, current_loadout
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (result_id) DO UPDATE SET
          |  battle_id = EXCLUDED.battle_id,
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
        PostgresBattleResultRecordMapper.bindRecord(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }

  override def list(
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Vector[BattleResultRecord] = {
    val conditions = Vector(
      handle.map(_ => "lower(handle) = lower(?)"),
      battleId.map(_ => "battle_id = ?")
    ).flatten
    val whereClause = Option.when(conditions.nonEmpty)(conditions.mkString(" WHERE ", " AND ", "")).getOrElse("")
    val sql =
      s"""SELECT battle_id, handle, display_name, finished_at, finished_at_label,
         |  duration_ms, score, placement, alive_at_end, rating_before,
         |  rating_delta, rating_after, result_label, mode_label, map_label,
         |  highlight_line, players_line, timeline_hint, current_loadout
         |FROM battle_results$whereClause
         |ORDER BY finished_at DESC, result_id ASC
         |LIMIT ?""".stripMargin

    queryMany(
      sql,
      statement => {
        var index = 1
        handle.foreach { value =>
          statement.setString(index, value.value.trim)
          index += 1
        }
        battleId.foreach { value =>
          statement.setString(index, value.value)
          index += 1
        }
        statement.setInt(index, math.max(0, limit))
      }
    )
  }

  private def queryMany(sql: String, bind: PreparedStatement => Unit): Vector[BattleResultRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val records = Vector.newBuilder[BattleResultRecord]
          while (resultSet.next()) {
            records += PostgresBattleResultRecordMapper.readRecord(resultSet)
          }
          records.result()
        }
      }
    }
}
