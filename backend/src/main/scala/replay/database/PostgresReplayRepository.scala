package slaydemo.backend.replay.database

import java.sql.{PreparedStatement, ResultSet, Types}

import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayRecord}
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final class PostgresReplayRepository(config: PostgresConfig) extends ReplayRepository {
  initialize()

  override def save(record: ReplayRecord): ReplayRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO replay_records (
          |  replay_id, battle_id, handle, display_name, finished_at, finished_at_label,
          |  title, mode_label, result_label, map_label, highlight_line, cover_label,
          |  players_line, timeline_hint, score, placement, duration_ms, alive_at_end,
          |  thumbnail_data_url, current_loadout, frame_count, playback_available, frames_json_b64
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
          |  frames_json_b64 = EXCLUDED.frames_json_b64""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }

    record
  }

  override def list(limit: Int): Seq[ReplayRecord] = {
    queryMany(
      """SELECT replay_id, battle_id, handle, display_name, finished_at, finished_at_label,
        |  title, mode_label, result_label, map_label, highlight_line, cover_label,
        |  players_line, timeline_hint, score, placement, duration_ms, alive_at_end,
        |  thumbnail_data_url, current_loadout, frame_count, playback_available, frames_json_b64
        |FROM replay_records
        |ORDER BY finished_at DESC
        |LIMIT ?""".stripMargin,
      statement => statement.setInt(1, limit.max(0))
    )
  }

  override def findById(replayId: ReplayId): Option[ReplayRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT replay_id, battle_id, handle, display_name, finished_at, finished_at_label,
          |  title, mode_label, result_label, map_label, highlight_line, cover_label,
          |  players_line, timeline_hint, score, placement, duration_ms, alive_at_end,
          |  thumbnail_data_url, current_loadout, frame_count, playback_available, frames_json_b64
          |FROM replay_records
          |WHERE replay_id = ?
          |LIMIT 1""".stripMargin
      ) { statement =>
        statement.setString(1, replayId.value)
        PostgresSupport.withResultSet(statement) { resultSet =>
          if (resultSet.next()) {
            try Some(readRecord(resultSet))
            catch {
              case error: Throwable =>
                Console.err.println(
                  s"[replay] skipping corrupted replay ${replayId.value}: ${error.getMessage}"
                )
                delete(replayId)
                None
            }
          } else {
            None
          }
        }
      }
    }
  }

  override def delete(replayId: ReplayId): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, "DELETE FROM replay_comments WHERE replay_id = ?") { statement =>
        statement.setString(1, replayId.value)
        statement.executeUpdate()
      }

      PostgresSupport.withStatement(connection, "DELETE FROM replay_records WHERE replay_id = ?") { statement =>
        statement.setString(1, replayId.value)
        statement.executeUpdate()
      }
    }
  }

  override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO replay_comments (
          |  comment_id, replay_id, author_handle, body, created_at
          |) VALUES (?, ?, ?, ?, ?)
          |ON CONFLICT (comment_id) DO UPDATE SET
          |  replay_id = EXCLUDED.replay_id,
          |  author_handle = EXCLUDED.author_handle,
          |  body = EXCLUDED.body,
          |  created_at = EXCLUDED.created_at""".stripMargin
      ) { statement =>
        bindComment(statement, record)
        statement.executeUpdate()
      }
    }

    record
  }

  override def listComments(replayId: ReplayId, limit: Int): Seq[ReplayCommentRecord] = {
    queryComments(
      """SELECT comment_id, replay_id, author_handle, body, created_at
        |FROM replay_comments
        |WHERE replay_id = ?
        |ORDER BY created_at ASC, comment_id ASC
        |LIMIT ?""".stripMargin,
      statement => {
        statement.setString(1, replayId.value)
        statement.setInt(2, limit.max(0))
      }
    )
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
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
          |  frames_json_b64 TEXT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

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

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS replay_comments_replay_id_created_at_idx ON replay_comments (replay_id, created_at ASC)"
      )(_.executeUpdate())
    }
  }

  private def queryMany(sql: String, bind: PreparedStatement => Unit): Seq[ReplayRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val records = Vector.newBuilder[ReplayRecord]
          val corruptedReplayIds = scala.collection.mutable.ArrayBuffer.empty[ReplayId]
          while (resultSet.next()) {
            try {
              records += readRecord(resultSet)
            } catch {
              case error: Throwable =>
                val badReplayId = try {
                  Option(resultSet.getString("replay_id")).map(_.trim).filter(_.nonEmpty).map(ReplayId.apply)
                } catch {
                  case _: Throwable => None
                }
                badReplayId.foreach(corruptedReplayIds += _)
                Console.err.println(
                  s"[replay] skipping corrupted replay row${badReplayId.map(id => s" ${id.value}").getOrElse("")}: ${error.getMessage}"
                )
            }
          }
          corruptedReplayIds.foreach(delete)
          records.result()
        }
      }
    }
  }

  private def queryComments(sql: String, bind: PreparedStatement => Unit): Seq[ReplayCommentRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(connection, sql) { statement =>
        bind(statement)
        PostgresSupport.withResultSet(statement) { resultSet =>
          val records = Vector.newBuilder[ReplayCommentRecord]
          while (resultSet.next()) {
            try {
              records += readComment(resultSet)
            } catch {
              case error: Throwable =>
                val badCommentId = try {
                  Option(resultSet.getString("comment_id")).map(_.trim).filter(_.nonEmpty)
                } catch {
                  case _: Throwable => None
                }
                Console.err.println(
                  s"[replay] skipping corrupted replay comment row${badCommentId.map(id => s" $id").getOrElse("")}: ${error.getMessage}"
                )
            }
          }
          records.result()
        }
      }
    }
  }

  private def bindRecord(statement: PreparedStatement, record: ReplayRecord): Unit = {
    statement.setString(1, record.replayId.value)
    statement.setString(2, record.battleId.value)
    statement.setString(3, record.handle.value)
    statement.setString(4, record.displayName)
    statement.setLong(5, record.finishedAt)
    statement.setString(6, record.finishedAtLabel)
    statement.setString(7, record.title)
    statement.setString(8, record.modeLabel)
    statement.setString(9, record.resultLabel)
    statement.setString(10, record.mapLabel)
    statement.setString(11, record.highlightLine)
    statement.setString(12, record.coverLabel)
    statement.setString(13, record.playersLine)
    statement.setString(14, record.timelineHint)
    statement.setInt(15, record.score)
    record.placement match {
      case Some(value) => statement.setInt(16, value)
      case None        => statement.setNull(16, Types.INTEGER)
    }
    statement.setLong(17, record.durationMs)
    statement.setBoolean(18, record.aliveAtEnd)
    record.thumbnailDataUrl match {
      case Some(value) => statement.setString(19, value)
      case None        => statement.setNull(19, Types.VARCHAR)
    }
    record.currentLoadout match {
      case Some(value) => statement.setString(20, value)
      case None        => statement.setNull(20, Types.VARCHAR)
    }
    statement.setInt(21, record.frameCount)
    statement.setBoolean(22, record.playbackAvailable)
    statement.setString(23, record.framesJsonB64)
  }

  private def readRecord(resultSet: ResultSet): ReplayRecord = {
    val placement = readNullableInt(resultSet, "placement")
    ReplayRecord(
      replayId = ReplayId(readString(resultSet, "replay_id")),
      battleId = BattleId(readString(resultSet, "battle_id")),
      handle = UserId(readString(resultSet, "handle")),
      displayName = readString(resultSet, "display_name"),
      finishedAt = readLong(resultSet, "finished_at"),
      finishedAtLabel = readString(resultSet, "finished_at_label"),
      title = readString(resultSet, "title"),
      modeLabel = readString(resultSet, "mode_label"),
      resultLabel = readString(resultSet, "result_label"),
      mapLabel = readString(resultSet, "map_label"),
      highlightLine = readString(resultSet, "highlight_line"),
      coverLabel = readString(resultSet, "cover_label"),
      playersLine = readString(resultSet, "players_line"),
      timelineHint = readString(resultSet, "timeline_hint"),
      score = readInt(resultSet, "score"),
      placement = placement,
      durationMs = readLong(resultSet, "duration_ms"),
      aliveAtEnd = readBoolean(resultSet, "alive_at_end"),
      thumbnailDataUrl = Option(resultSet.getString("thumbnail_data_url")),
      currentLoadout = Option(resultSet.getString("current_loadout")),
      frameCount = readInt(resultSet, "frame_count"),
      playbackAvailable = readBoolean(resultSet, "playback_available"),
      framesJsonB64 = readString(resultSet, "frames_json_b64")
    )
  }

  private def bindComment(statement: PreparedStatement, record: ReplayCommentRecord): Unit = {
    statement.setString(1, record.id)
    statement.setString(2, record.replayId.value)
    statement.setString(3, record.authorHandle.value)
    statement.setString(4, record.body)
    statement.setLong(5, record.createdAt)
  }

  private def readComment(resultSet: ResultSet): ReplayCommentRecord = {
    ReplayCommentRecord(
      id = readString(resultSet, "comment_id"),
      replayId = ReplayId(readString(resultSet, "replay_id")),
      authorHandle = UserId(readString(resultSet, "author_handle")),
      body = readString(resultSet, "body"),
      createdAt = readLong(resultSet, "created_at")
    )
  }

  private def readString(resultSet: ResultSet, column: String): String = {
    Option(resultSet.getString(column)).getOrElse("")
  }

  private def readInt(resultSet: ResultSet, column: String): Int = {
    val value = resultSet.getInt(column)
    if (resultSet.wasNull()) 0 else value
  }

  private def readLong(resultSet: ResultSet, column: String): Long = {
    val value = resultSet.getLong(column)
    if (resultSet.wasNull()) 0L else value
  }

  private def readBoolean(resultSet: ResultSet, column: String): Boolean = {
    resultSet.getBoolean(column) && !resultSet.wasNull()
  }

  private def readNullableInt(resultSet: ResultSet, column: String): Option[Int] = {
    val value = resultSet.getInt(column)
    if (resultSet.wasNull()) None else Some(value)
  }
}
