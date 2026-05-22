package services.replay.database

import java.sql.Connection

import services.replay.database.PostgresReplayRecordMapper.*
import services.replay.objects.{ReplayId, ReplayRecord, ReplaySettlementRecord}
import system.database.PostgresSupport

private[database] object PostgresReplaySettlementQueries {
  def replaceSettlements(connection: Connection, record: ReplayRecord): Unit = {
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
        bindOptionalInt(statement, 7, settlement.placement.map(_.value))
        bindOptionalInt(statement, 8, settlement.ratingBefore.map(_.value))
        bindOptionalInt(statement, 9, settlement.ratingDelta.map(_.value))
        bindOptionalInt(statement, 10, settlement.ratingAfter.map(_.value))
        statement.setBoolean(11, settlement.aliveAtEnd)
        bindOptionalString(statement, 12, settlement.currentLoadout)
        statement.executeUpdate()
      }
    }
  }

  def listSettlements(connection: Connection, replayId: ReplayId): Vector[ReplaySettlementRecord] =
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
}
