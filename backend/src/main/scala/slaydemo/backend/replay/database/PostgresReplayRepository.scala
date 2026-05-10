package slaydemo.backend.replay.database

import java.sql.{Connection, PreparedStatement}

import slaydemo.backend.replay.objects.{ReplayCommentId, ReplayCommentRecord, ReplayId, ReplayRecord}
import slaydemo.backend.replay.database.PostgresReplayRecordMapper.*
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresReplayRepository(
  settings: PostgresConnectionSettings,
  commentIdGenerator: ReplayCommentIdGenerator = RandomReplayCommentIdGenerator
) extends ReplayRepository {
  PostgresReplaySchema.initialize(settings)

  override def saveReplay(record: ReplayRecord): ReplayRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withTransaction(connection) {
        PostgresSupport.withStatement(
          connection,
          """INSERT INTO replay_records (
            |  replay_id, battle_id, handle, display_name, finished_at, finished_at_label,
            |  title, mode_label, result_label, map_label, highlight_line, cover_label,
            |  players_line, timeline_hint, score, placement, duration_ms, alive_at_end,
            |  thumbnail_data_url, current_loadout, frame_count, playback_available, frames_json_b64,
            |  rating_before, rating_delta, rating_after
            |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            |ON CONFLICT (replay_id) DO UPDATE SET
            |  battle_id = EXCLUDED.battle_id,
            |  handle = EXCLUDED.handle,
            |  display_name = EXCLUDED.display_name,
            |  finished_at = EXCLUDED.finished_at,
            |  finished_at_label = EXCLUDED.finished_at_label,
            |  title = EXCLUDED.title,
            |  mode_label = EXCLUDED.mode_label,
            |  result_label = EXCLUDED.result_label,
            |  map_label = EXCLUDED.map_label,
            |  highlight_line = EXCLUDED.highlight_line,
            |  cover_label = EXCLUDED.cover_label,
            |  players_line = EXCLUDED.players_line,
            |  timeline_hint = EXCLUDED.timeline_hint,
            |  score = EXCLUDED.score,
            |  placement = EXCLUDED.placement,
            |  duration_ms = EXCLUDED.duration_ms,
            |  alive_at_end = EXCLUDED.alive_at_end,
            |  thumbnail_data_url = EXCLUDED.thumbnail_data_url,
            |  current_loadout = EXCLUDED.current_loadout,
            |  frame_count = EXCLUDED.frame_count,
            |  playback_available = EXCLUDED.playback_available,
            |  frames_json_b64 = EXCLUDED.frames_json_b64,
            |  rating_before = EXCLUDED.rating_before,
            |  rating_delta = EXCLUDED.rating_delta,
            |  rating_after = EXCLUDED.rating_after""".stripMargin
        ) { statement =>
          bindReplay(statement, record)
          statement.executeUpdate()
        }
        PostgresReplaySettlementQueries.replaceSettlements(connection, record)
      }
    }
    record
  }

  override def listReplays(limit: Int): Vector[ReplayRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      val records = queryReplays(
        connection,
        s"""SELECT $replayColumns
           |FROM replay_records
           |ORDER BY finished_at DESC, replay_id ASC
           |LIMIT ?""".stripMargin,
        statement => statement.setInt(1, math.max(0, limit))
      )
      withSettlements(connection, records)
    }

  override def findReplayById(replayId: ReplayId): Option[ReplayRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      var record = Option.empty[ReplayRecord]
      PostgresSupport.withStatement(
        connection,
        s"""SELECT $replayColumns
           |FROM replay_records
           |WHERE replay_id = ?
           |LIMIT 1""".stripMargin
      ) { statement =>
        statement.setString(1, replayId.value)
        PostgresSupport.withResultSet(statement) { resultSet =>
          record = if (resultSet.next()) Some(readReplay(resultSet)) else None
        }
      }
      record.map(item => item.copy(settlements = PostgresReplaySettlementQueries.listSettlements(connection, replayId)))
    }

  override def nextCommentId(): ReplayCommentId =
    commentIdGenerator.nextCommentId()

  override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      PostgresReplayCommentQueries.saveComment(connection, record)
    }
    record
  }

  override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresReplayCommentQueries.listComments(connection, replayId, limit)
    }

  private val replayColumns: String =
    """replay_id, battle_id, handle, display_name, finished_at, finished_at_label,
      |  title, mode_label, result_label, map_label, highlight_line, cover_label,
      |  players_line, timeline_hint, score, placement, duration_ms, alive_at_end,
      |  thumbnail_data_url, current_loadout, frame_count, playback_available, frames_json_b64,
      |  rating_before, rating_delta, rating_after""".stripMargin

  private def queryReplays(
    connection: Connection,
    sql: String,
    bind: PreparedStatement => Unit
  ): Vector[ReplayRecord] =
    PostgresSupport.withStatement(connection, sql) { statement =>
      bind(statement)
      PostgresSupport.withResultSet(statement) { resultSet =>
        val records = Vector.newBuilder[ReplayRecord]
        while (resultSet.next()) {
          records += readReplay(resultSet)
        }
        records.result()
      }
    }

  private def withSettlements(connection: Connection, records: Vector[ReplayRecord]): Vector[ReplayRecord] =
    records.map(record => record.copy(settlements = PostgresReplaySettlementQueries.listSettlements(connection, record.replayId)))

}
