package slaydemo.backend.replay.database

import java.nio.charset.StandardCharsets
import java.sql.{Connection, PreparedStatement, ResultSet, Types}
import java.util.{Base64, UUID}
import scala.util.control.NonFatal

import slaydemo.backend.battle.objects.{BattleId, DurationMillis, EpochMillis, Rating, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.objects.{ReplayCommentId, ReplayCommentRecord, ReplayId, ReplayRecord, ReplaySettlementRecord}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresReplayRepository(settings: PostgresConnectionSettings) extends ReplayRepository {
  initialize()

  override def saveReplay(record: ReplayRecord): ReplayRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      val previousAutoCommit = connection.getAutoCommit
      connection.setAutoCommit(false)
      try {
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
        replaceSettlements(connection, record)
        connection.commit()
      } catch {
        case NonFatal(error) =>
          connection.rollback()
          throw error
      } finally {
        connection.setAutoCommit(previousAutoCommit)
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
      record.map(item => item.copy(settlements = listSettlements(connection, replayId)))
    }

  override def nextCommentId(): ReplayCommentId =
    ReplayCommentId(s"comment-${UUID.randomUUID().toString}")

  override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord = {
    PostgresSupport.withConnection(settings) { connection =>
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
        statement.setString(1, record.id.value)
        statement.setString(2, record.replayId.value)
        statement.setString(3, record.authorHandle.value)
        statement.setString(4, record.body)
        statement.setLong(5, record.createdAt.value)
        statement.executeUpdate()
      }
    }
    record
  }

  override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT comment_id, replay_id, author_handle, body, created_at
          |FROM (
          |  SELECT comment_id, replay_id, author_handle, body, created_at
          |  FROM replay_comments
          |  WHERE replay_id = ?
          |  ORDER BY created_at DESC, comment_id DESC
          |  LIMIT ?
          |) recent
          |ORDER BY created_at ASC, comment_id ASC""".stripMargin
      ) { statement =>
        statement.setString(1, replayId.value)
        statement.setInt(2, math.max(0, limit))
        PostgresSupport.withResultSet(statement)(readComments)
      }
    }

  private def initialize(): Unit =
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
    records.map(record => record.copy(settlements = listSettlements(connection, record.replayId)))

  private def replaceSettlements(connection: Connection, record: ReplayRecord): Unit = {
    PostgresSupport.withStatement(
      connection,
      "DELETE FROM replay_settlements WHERE replay_id = ?"
    ) { statement =>
      statement.setString(1, record.replayId.value)
      statement.executeUpdate()
    }

    record.settlements.foreach { settlement =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO replay_settlements (
          |  replay_id, handle, display_name, result_label, highlight_line, score,
          |  placement, rating_before, rating_delta, rating_after, alive_at_end, current_loadout
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      ) { statement =>
        statement.setString(1, record.replayId.value)
        statement.setString(2, settlement.handle.value)
        statement.setString(3, settlement.displayName.value)
        statement.setString(4, settlement.resultLabel)
        statement.setString(5, settlement.highlightLine)
        statement.setInt(6, settlement.score.value)
        bindOptionalInt(statement, 7, settlement.placement)
        bindOptionalInt(statement, 8, settlement.ratingBefore.map(_.value))
        bindOptionalInt(statement, 9, settlement.ratingDelta)
        bindOptionalInt(statement, 10, settlement.ratingAfter.map(_.value))
        statement.setBoolean(11, settlement.aliveAtEnd)
        bindOptionalString(statement, 12, settlement.currentLoadout)
        statement.executeUpdate()
      }
    }
  }

  private def listSettlements(connection: Connection, replayId: ReplayId): Vector[ReplaySettlementRecord] =
    PostgresSupport.withStatement(
      connection,
      """SELECT handle, display_name, result_label, highlight_line, score, placement,
        |  rating_before, rating_delta, rating_after, alive_at_end, current_loadout
        |FROM replay_settlements
        |WHERE replay_id = ?
        |ORDER BY COALESCE(placement, 2147483647), handle ASC""".stripMargin
    ) { statement =>
      statement.setString(1, replayId.value)
      PostgresSupport.withResultSet(statement) { resultSet =>
        val settlements = Vector.newBuilder[ReplaySettlementRecord]
        while (resultSet.next()) {
          settlements += readSettlement(resultSet)
        }
        settlements.result()
      }
    }

  private def bindReplay(statement: PreparedStatement, record: ReplayRecord): Unit = {
    statement.setString(1, record.replayId.value)
    statement.setString(2, record.battleId.value)
    statement.setString(3, record.handle.value)
    statement.setString(4, record.displayName.value)
    statement.setLong(5, record.finishedAt.value)
    statement.setString(6, record.finishedAtLabel)
    statement.setString(7, record.title)
    statement.setString(8, record.modeLabel)
    statement.setString(9, record.resultLabel)
    statement.setString(10, record.mapLabel)
    statement.setString(11, record.highlightLine)
    statement.setString(12, record.coverLabel)
    statement.setString(13, record.playersLine)
    statement.setString(14, record.timelineHint)
    statement.setInt(15, record.score.value)
    record.placement match {
      case Some(value) => statement.setInt(16, value)
      case None        => statement.setNull(16, Types.INTEGER)
    }
    statement.setLong(17, record.durationMs.value)
    statement.setBoolean(18, record.aliveAtEnd)
    bindOptionalString(statement, 19, record.thumbnailDataUrl)
    bindOptionalString(statement, 20, record.currentLoadout)
    statement.setInt(21, record.frameCount)
    statement.setBoolean(22, record.playbackAvailable)
    statement.setString(23, encodeFramesJson(record.framesJson))
    bindOptionalInt(statement, 24, record.ratingBefore.map(_.value))
    bindOptionalInt(statement, 25, record.ratingDelta)
    bindOptionalInt(statement, 26, record.ratingAfter.map(_.value))
  }

  private def bindOptionalString(statement: PreparedStatement, index: Int, value: Option[String]): Unit =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(text) => statement.setString(index, text)
      case None       => statement.setNull(index, Types.VARCHAR)
    }

  private def bindOptionalInt(statement: PreparedStatement, index: Int, value: Option[Int]): Unit =
    value match {
      case Some(number) => statement.setInt(index, number)
      case None         => statement.setNull(index, Types.INTEGER)
    }

  private def readReplay(resultSet: ResultSet): ReplayRecord =
    ReplayRecord(
      replayId = ReplayId(resultSet.getString("replay_id")),
      battleId = BattleId(resultSet.getString("battle_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      finishedAt = EpochMillis(resultSet.getLong("finished_at")),
      finishedAtLabel = resultSet.getString("finished_at_label"),
      title = resultSet.getString("title"),
      modeLabel = resultSet.getString("mode_label"),
      resultLabel = resultSet.getString("result_label"),
      mapLabel = resultSet.getString("map_label"),
      highlightLine = resultSet.getString("highlight_line"),
      coverLabel = resultSet.getString("cover_label"),
      playersLine = resultSet.getString("players_line"),
      timelineHint = resultSet.getString("timeline_hint"),
      score = Score(resultSet.getInt("score")),
      placement = optionalIntColumn(resultSet, "placement"),
      ratingBefore = optionalIntColumn(resultSet, "rating_before").map(Rating.apply),
      ratingDelta = optionalIntColumn(resultSet, "rating_delta"),
      ratingAfter = optionalIntColumn(resultSet, "rating_after").map(Rating.apply),
      durationMs = DurationMillis(resultSet.getLong("duration_ms")),
      aliveAtEnd = resultSet.getBoolean("alive_at_end"),
      thumbnailDataUrl = optionalColumn(resultSet, "thumbnail_data_url"),
      currentLoadout = optionalColumn(resultSet, "current_loadout"),
      frameCount = resultSet.getInt("frame_count"),
      playbackAvailable = resultSet.getBoolean("playback_available"),
      framesJson = decodeFramesJson(resultSet.getString("frames_json_b64"))
    )

  private def readSettlement(resultSet: ResultSet): ReplaySettlementRecord =
    ReplaySettlementRecord(
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      resultLabel = resultSet.getString("result_label"),
      highlightLine = resultSet.getString("highlight_line"),
      score = Score(resultSet.getInt("score")),
      placement = optionalIntColumn(resultSet, "placement"),
      ratingBefore = optionalIntColumn(resultSet, "rating_before").map(Rating.apply),
      ratingDelta = optionalIntColumn(resultSet, "rating_delta"),
      ratingAfter = optionalIntColumn(resultSet, "rating_after").map(Rating.apply),
      aliveAtEnd = resultSet.getBoolean("alive_at_end"),
      currentLoadout = optionalColumn(resultSet, "current_loadout")
    )

  private def readComments(resultSet: ResultSet): Vector[ReplayCommentRecord] = {
    val comments = Vector.newBuilder[ReplayCommentRecord]
    while (resultSet.next()) {
      comments += ReplayCommentRecord(
        id = ReplayCommentId(resultSet.getString("comment_id")),
        replayId = ReplayId(resultSet.getString("replay_id")),
        authorHandle = PlayerHandle(resultSet.getString("author_handle")),
        body = resultSet.getString("body"),
        createdAt = EpochMillis(resultSet.getLong("created_at"))
      )
    }
    comments.result()
  }

  private def encodeFramesJson(value: String): String =
    Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))

  private def decodeFramesJson(value: String): String =
    try {
      new String(Base64.getDecoder.decode(Option(value).getOrElse("")), StandardCharsets.UTF_8)
    } catch {
      case _: IllegalArgumentException => "[]"
    }

  private def optionalColumn(resultSet: ResultSet, columnName: String): Option[String] =
    Option(resultSet.getString(columnName)).map(_.trim).filter(_.nonEmpty)

  private def optionalIntColumn(resultSet: ResultSet, columnName: String): Option[Int] = {
    val value = resultSet.getInt(columnName)
    if resultSet.wasNull() then None else Some(value)
  }
}
