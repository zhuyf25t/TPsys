package slaydemo.backend.governance.database

import java.sql.{PreparedStatement, ResultSet}

import slaydemo.backend.governance.objects.ContributionAdjustmentRecord
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}

final class PostgresContributionAdjustmentRepository(config: PostgresConfig) extends ContributionAdjustmentRepository {
  initialize()

  override def list(limit: Int): Seq[ContributionAdjustmentRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT id, actor_handle, target_handle, delta, reason, created_at, source_label, source_path
          |FROM governance_contribution_adjustments
          |ORDER BY created_at DESC, id DESC
          |LIMIT ?""".stripMargin
      ) { statement =>
        statement.setInt(1, math.max(0, limit))
        PostgresSupport.withResultSet(statement)(readRecords)
      }
    }
  }

  override def save(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO governance_contribution_adjustments (
          |  id, actor_handle, target_handle, delta, reason, created_at, source_label, source_path
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      ) { statement =>
        statement.setString(1, record.id)
        statement.setString(2, record.actorHandle)
        statement.setString(3, record.targetHandle)
        statement.setInt(4, record.delta)
        statement.setString(5, record.reason)
        statement.setLong(6, record.createdAt)
        statement.setString(7, record.sourceLabel)
        statement.setString(8, record.sourcePath)
        statement.executeUpdate()
      }
    }

    record
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS governance_contribution_adjustments (
          |  id TEXT PRIMARY KEY,
          |  actor_handle TEXT NOT NULL,
          |  target_handle TEXT NOT NULL,
          |  delta INTEGER NOT NULL,
          |  reason TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  source_label TEXT NOT NULL DEFAULT '',
          |  source_path TEXT NOT NULL DEFAULT ''
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE governance_contribution_adjustments ADD COLUMN IF NOT EXISTS source_label TEXT NOT NULL DEFAULT ''"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "ALTER TABLE governance_contribution_adjustments ADD COLUMN IF NOT EXISTS source_path TEXT NOT NULL DEFAULT ''"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_contribution_adjustments_created_at_idx ON governance_contribution_adjustments (created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_contribution_adjustments_target_handle_idx ON governance_contribution_adjustments (lower(target_handle), created_at DESC)"
      )(_.executeUpdate())
    }
  }

  private def readRecords(resultSet: ResultSet): Seq[ContributionAdjustmentRecord] = {
    val buffer = scala.collection.mutable.ArrayBuffer.empty[ContributionAdjustmentRecord]
    while (resultSet.next()) {
      buffer += readRecord(resultSet)
    }
    buffer.toSeq
  }

  private def readRecord(resultSet: ResultSet): ContributionAdjustmentRecord = {
    ContributionAdjustmentRecord(
      id = resultSet.getString("id"),
      actorHandle = resultSet.getString("actor_handle"),
      targetHandle = resultSet.getString("target_handle"),
      delta = resultSet.getInt("delta"),
      reason = resultSet.getString("reason"),
      createdAt = resultSet.getLong("created_at"),
      sourceLabel = Option(resultSet.getString("source_label")).getOrElse(""),
      sourcePath = Option(resultSet.getString("source_path")).getOrElse("")
    )
  }
}
