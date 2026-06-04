package services.battle.microservices.results.database

import java.sql.{Connection, PreparedStatement, ResultSet, Types}

import cats.effect.IO

import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.battle.objects.{
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  DurationMillis,
  EpochMillis
}
import services.battle.microservices.results.objects.result.{
  BattleHighlightLine,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultLabel,
  BattleResultRecord,
  BattleTimelineHint,
  RatingDelta
}
import services.identity.objects.{DisplayName, PlayerHandle}
import system.database.PostgresSupport

object BattleResultTable {
  private enum SqlBindingValue {
    case Text(value: String)
    case IntValue(value: Int)
  }

  private final case class IndexedSqlBinding(index: Int, value: SqlBindingValue)

  private val upsertSql: String =
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

  def save(connection: Connection, record: BattleResultRecord): IO[BattleResultRecord] = IO.blocking {
    PostgresSupport.withStatement(connection, upsertSql) { statement =>
      bindRecord(statement, record)
      statement.executeUpdate()
    }
    record
  }

  def list(
    connection: Connection,
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): IO[Vector[BattleResultRecord]] = IO.blocking {
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
      connection,
      sql,
      statement => bindListParameters(statement, handle, battleId, limit)
    )
  }

  private def queryMany(
    connection: Connection,
    sql: String,
    bind: PreparedStatement => Unit
  ): Vector[BattleResultRecord] =
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

  private def bindListParameters(
    statement: PreparedStatement,
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Unit =
    listParameterBindings(handle, battleId, limit).foreach(bindSqlValue(statement, _))

  private def listParameterBindings(
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Vector[IndexedSqlBinding] =
    listParameterValues(handle, battleId, limit).zipWithIndex.map { case (value, offset) =>
      IndexedSqlBinding(index = offset + 1, value = value)
    }

  private def listParameterValues(
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Vector[SqlBindingValue] =
    Vector(
      handle.map(value => SqlBindingValue.Text(value.value.trim)),
      battleId.map(value => SqlBindingValue.Text(value.value))
    ).flatten :+ SqlBindingValue.IntValue(math.max(0, limit))

  private def bindSqlValue(statement: PreparedStatement, binding: IndexedSqlBinding): Unit =
    binding.value match {
      case SqlBindingValue.Text(value) =>
        statement.setString(binding.index, value)
      case SqlBindingValue.IntValue(value) =>
        statement.setInt(binding.index, value)
    }

  private def bindRecord(statement: PreparedStatement, record: BattleResultRecord): Unit = {
    statement.setString(1, record.resultId.value)
    statement.setString(2, record.battleId.value)
    statement.setString(3, record.handle.value)
    statement.setString(4, record.displayName.value)
    statement.setLong(5, record.finishedAt.value)
    statement.setString(6, record.finishedAtLabel)
    statement.setLong(7, record.durationMs.value)
    statement.setInt(8, record.score.value)
    record.placement match {
      case Some(value) => statement.setInt(9, value.value)
      case None        => statement.setNull(9, Types.INTEGER)
    }
    statement.setBoolean(10, record.aliveAtEnd)
    statement.setInt(11, record.ratingBefore.value)
    statement.setInt(12, record.ratingDelta.value)
    statement.setInt(13, record.ratingAfter.value)
    statement.setString(14, record.resultLabel.value)
    statement.setString(15, record.modeLabel.value)
    statement.setString(16, record.mapLabel.value)
    statement.setString(17, record.highlightLine.value)
    statement.setString(18, record.playersLine.value)
    statement.setString(19, record.timelineHint.value)
    record.currentLoadout match {
      case Some(value) => statement.setString(20, value)
      case None        => statement.setNull(20, Types.VARCHAR)
    }
  }

  private def readRecord(resultSet: ResultSet): BattleResultRecord = {
    val placement = resultSet.getInt("placement")
    BattleResultRecord(
      battleId = BattleId(resultSet.getString("battle_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      finishedAt = EpochMillis(resultSet.getLong("finished_at")),
      finishedAtLabel = resultSet.getString("finished_at_label"),
      durationMs = DurationMillis(resultSet.getLong("duration_ms")),
      score = Score(resultSet.getInt("score")),
      placement = if (resultSet.wasNull()) None else BattlePlacement.fromWire(placement),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(resultSet.getBoolean("alive_at_end")),
      ratingBefore = Rating(resultSet.getInt("rating_before")),
      ratingDelta = RatingDelta(resultSet.getInt("rating_delta")),
      ratingAfter = Rating(resultSet.getInt("rating_after")),
      resultLabel = BattleResultLabel.fromWire(resultSet.getString("result_label")),
      modeLabel = BattleModeLabel.fromWire(resultSet.getString("mode_label")),
      mapLabel = BattleMapLabel.fromWire(resultSet.getString("map_label")),
      highlightLine = BattleHighlightLine.fromWire(resultSet.getString("highlight_line")),
      playersLine = BattlePlayersLine.fromWire(resultSet.getString("players_line")),
      timelineHint = BattleTimelineHint.fromWire(resultSet.getString("timeline_hint")),
      currentLoadout = Option(resultSet.getString("current_loadout"))
    )
  }
}
