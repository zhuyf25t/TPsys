package slaydemo.backend.battle.database

import java.sql.{PreparedStatement, ResultSet, Types}

import slaydemo.backend.battle.objects.{
  BattleId,
  BattleHighlightLine,
  BattleMapLabel,
  BattleModeLabel,
  BattleResultRecord,
  BattleResultLabel,
  BattlePlayersLine,
  BattlePlacement,
  BattleTimelineHint,
  BattleSurvivalOutcome,
  DurationMillis,
  EpochMillis,
  Rating,
  RatingDelta,
  Score
}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

private[database] object PostgresBattleResultRecordMapper {
  def bindRecord(statement: PreparedStatement, record: BattleResultRecord): Unit = {
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

  def readRecord(resultSet: ResultSet): BattleResultRecord = {
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
