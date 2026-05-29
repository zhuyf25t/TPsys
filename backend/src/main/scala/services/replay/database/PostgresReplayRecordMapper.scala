package services.replay.database

import java.sql.{PreparedStatement, ResultSet, Types}

import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.battle.microservices.results.objects.result.{BattlePlacement, RatingDelta}
import services.battle.objects.{BattleId, DurationMillis, EpochMillis}
import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.objects.{
  ReplayCommentId,
  ReplayCommentRecord,
  ReplayFrameCount,
  ReplayFramesJson,
  ReplayId,
  ReplayPlaybackAvailability,
  ReplayRecord,
  ReplaySettlementRecord,
  ReplayTitle
}
import services.replay.support.ReplayFramesJsonCodec

private[database] object PostgresReplayRecordMapper {
  def bindReplay(statement: PreparedStatement, record: ReplayRecord): Unit = {
    statement.setString(1, record.replayId.value)
    statement.setString(2, record.battleId.value)
    statement.setString(3, record.handle.value)
    statement.setString(4, record.displayName.value)
    statement.setLong(5, record.finishedAt.value)
    statement.setString(6, record.finishedAtLabel)
    statement.setString(7, record.title.value)
    statement.setString(8, record.modeLabel)
    statement.setString(9, record.resultLabel)
    statement.setString(10, record.mapLabel)
    statement.setString(11, record.highlightLine)
    statement.setString(12, record.coverLabel)
    statement.setString(13, record.playersLine)
    statement.setString(14, record.timelineHint)
    statement.setInt(15, record.score.value)
    record.placement match {
      case Some(value) => statement.setInt(16, value.value)
      case None        => statement.setNull(16, Types.INTEGER)
    }
    statement.setLong(17, record.durationMs.value)
    statement.setBoolean(18, record.aliveAtEnd)
    bindOptionalString(statement, 19, record.thumbnailDataUrl)
    bindOptionalString(statement, 20, record.currentLoadout)
    statement.setInt(21, record.frameCount.value)
    statement.setBoolean(22, record.playbackAvailable)
    statement.setString(23, ReplayFramesJsonCodec.encode(record.framesJson.value))
    bindOptionalInt(statement, 24, record.ratingBefore.map(_.value))
    bindOptionalInt(statement, 25, record.ratingDelta.map(_.value))
    bindOptionalInt(statement, 26, record.ratingAfter.map(_.value))
  }

  def bindOptionalString(statement: PreparedStatement, index: Int, value: Option[String]): Unit =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(text) => statement.setString(index, text)
      case None       => statement.setNull(index, Types.VARCHAR)
    }

  def bindOptionalInt(statement: PreparedStatement, index: Int, value: Option[Int]): Unit =
    value match {
      case Some(number) => statement.setInt(index, number)
      case None         => statement.setNull(index, Types.INTEGER)
    }

  def readReplay(resultSet: ResultSet): ReplayRecord =
    ReplayRecord(
      replayId = ReplayId(resultSet.getString("replay_id")),
      battleId = BattleId(resultSet.getString("battle_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      finishedAt = EpochMillis(resultSet.getLong("finished_at")),
      finishedAtLabel = resultSet.getString("finished_at_label"),
      title = ReplayTitle.fromWire(resultSet.getString("title")),
      modeLabel = resultSet.getString("mode_label"),
      resultLabel = resultSet.getString("result_label"),
      mapLabel = resultSet.getString("map_label"),
      highlightLine = resultSet.getString("highlight_line"),
      coverLabel = resultSet.getString("cover_label"),
      playersLine = resultSet.getString("players_line"),
      timelineHint = resultSet.getString("timeline_hint"),
      score = Score(resultSet.getInt("score")),
      placement = optionalIntColumn(resultSet, "placement").flatMap(BattlePlacement.fromWire),
      ratingBefore = optionalIntColumn(resultSet, "rating_before").map(Rating.apply),
      ratingDelta = optionalIntColumn(resultSet, "rating_delta").map(RatingDelta.apply),
      ratingAfter = optionalIntColumn(resultSet, "rating_after").map(Rating.apply),
      durationMs = DurationMillis(resultSet.getLong("duration_ms")),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(resultSet.getBoolean("alive_at_end")),
      thumbnailDataUrl = optionalColumn(resultSet, "thumbnail_data_url"),
      currentLoadout = optionalColumn(resultSet, "current_loadout"),
      frameCount = ReplayFrameCount.fromWire(resultSet.getInt("frame_count")),
      playbackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(resultSet.getBoolean("playback_available")),
      framesJson = ReplayFramesJson.fromNormalized(ReplayFramesJsonCodec.decode(resultSet.getString("frames_json_b64")))
    )

  def readSettlement(resultSet: ResultSet): ReplaySettlementRecord =
    ReplaySettlementRecord(
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      resultLabel = resultSet.getString("result_label"),
      highlightLine = resultSet.getString("highlight_line"),
      score = Score(resultSet.getInt("score")),
      placement = optionalIntColumn(resultSet, "placement").flatMap(BattlePlacement.fromWire),
      ratingBefore = optionalIntColumn(resultSet, "rating_before").map(Rating.apply),
      ratingDelta = optionalIntColumn(resultSet, "rating_delta").map(RatingDelta.apply),
      ratingAfter = optionalIntColumn(resultSet, "rating_after").map(Rating.apply),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(resultSet.getBoolean("alive_at_end")),
      currentLoadout = optionalColumn(resultSet, "current_loadout")
    )

  def readComments(resultSet: ResultSet): Vector[ReplayCommentRecord] = {
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

  private def optionalColumn(resultSet: ResultSet, columnName: String): Option[String] =
    Option(resultSet.getString(columnName)).map(_.trim).filter(_.nonEmpty)

  private def optionalIntColumn(resultSet: ResultSet, columnName: String): Option[Int] = {
    val value = resultSet.getInt(columnName)
    if resultSet.wasNull() then None else Some(value)
  }
}
