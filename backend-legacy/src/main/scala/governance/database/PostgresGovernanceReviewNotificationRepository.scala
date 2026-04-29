package slaydemo.backend.governance.database

import java.sql.{PreparedStatement, ResultSet, Types}

import slaydemo.backend.governance.objects.GovernanceReviewNotificationRecord
import slaydemo.backend.shared.database.{PostgresConfig, PostgresSupport}

final class PostgresGovernanceReviewNotificationRepository(config: PostgresConfig)
    extends GovernanceReviewNotificationRepository {
  initialize()

  override def list(
    kind: Option[String],
    targetType: Option[String],
    limit: Int
  ): Seq[GovernanceReviewNotificationRecord] = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT id, actor_handle, kind, target_type, target_id, target_title, target_path, body, created_at, mail_id
          |FROM governance_review_notifications
          |WHERE (? IS NULL OR kind = ?) AND (? IS NULL OR target_type = ?)
          |ORDER BY created_at DESC, id DESC
          |LIMIT ?""".stripMargin
      ) { statement =>
        bindOptionalString(statement, 1, kind.map(_.trim).filter(_.nonEmpty))
        bindOptionalString(statement, 3, targetType.map(_.trim).filter(_.nonEmpty))
        statement.setInt(5, math.max(0, limit))
        PostgresSupport.withResultSet(statement)(readRecords)
      }
    }
  }

  override def save(record: GovernanceReviewNotificationRecord): GovernanceReviewNotificationRecord = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO governance_review_notifications (
          |  id, actor_handle, kind, target_type, target_id, target_title, target_path, body, created_at, mail_id
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      ) { statement =>
        bindRecord(statement, record)
        statement.executeUpdate()
      }
    }

    record
  }

  override def findByMailId(mailId: String): Option[GovernanceReviewNotificationRecord] = {
    val normalizedMailId = mailId.trim
    if (normalizedMailId.isEmpty) {
      None
    } else {
      PostgresSupport.withConnection(config) { connection =>
        PostgresSupport.withStatement(
          connection,
          """SELECT id, actor_handle, kind, target_type, target_id, target_title, target_path, body, created_at, mail_id
            |FROM governance_review_notifications
            |WHERE mail_id = ?
            |LIMIT 1""".stripMargin
        ) { statement =>
          statement.setString(1, normalizedMailId)
          PostgresSupport.withResultSet(statement) { resultSet =>
            if (resultSet.next()) Some(readRecord(resultSet)) else None
          }
        }
      }
    }
  }

  private def initialize(): Unit = {
    PostgresSupport.withConnection(config) { connection =>
      PostgresSupport.withStatement(
        connection,
        """CREATE TABLE IF NOT EXISTS governance_review_notifications (
          |  id TEXT PRIMARY KEY,
          |  actor_handle TEXT NOT NULL,
          |  kind TEXT NOT NULL,
          |  target_type TEXT NOT NULL,
          |  target_id TEXT NOT NULL,
          |  target_title TEXT NOT NULL,
          |  target_path TEXT NOT NULL,
          |  body TEXT NOT NULL,
          |  created_at BIGINT NOT NULL,
          |  mail_id TEXT NOT NULL
          |)""".stripMargin
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_created_at_idx ON governance_review_notifications (created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_kind_created_at_idx ON governance_review_notifications (kind, created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_target_type_created_at_idx ON governance_review_notifications (target_type, created_at DESC)"
      )(_.executeUpdate())

      PostgresSupport.withStatement(
        connection,
        "CREATE INDEX IF NOT EXISTS governance_review_notifications_mail_id_idx ON governance_review_notifications (mail_id)"
      )(_.executeUpdate())
    }
  }

  private def bindOptionalString(statement: PreparedStatement, index: Int, value: Option[String]): Unit = {
    value match {
      case Some(text) =>
        statement.setString(index, text)
        statement.setString(index + 1, text)
      case None =>
        statement.setNull(index, Types.VARCHAR)
        statement.setNull(index + 1, Types.VARCHAR)
    }
  }

  private def bindRecord(statement: PreparedStatement, record: GovernanceReviewNotificationRecord): Unit = {
    statement.setString(1, record.id)
    statement.setString(2, record.actorHandle)
    statement.setString(3, record.kind)
    statement.setString(4, record.targetType)
    statement.setString(5, record.targetId)
    statement.setString(6, record.targetTitle)
    statement.setString(7, record.targetPath)
    statement.setString(8, record.body)
    statement.setLong(9, record.createdAt)
    statement.setString(10, record.mailId)
  }

  private def readRecords(resultSet: ResultSet): Seq[GovernanceReviewNotificationRecord] = {
    val buffer = scala.collection.mutable.ArrayBuffer.empty[GovernanceReviewNotificationRecord]
    while (resultSet.next()) {
      buffer += readRecord(resultSet)
    }
    buffer.toSeq
  }

  private def readRecord(resultSet: ResultSet): GovernanceReviewNotificationRecord = {
    GovernanceReviewNotificationRecord(
      id = resultSet.getString("id"),
      actorHandle = resultSet.getString("actor_handle"),
      kind = resultSet.getString("kind"),
      targetType = resultSet.getString("target_type"),
      targetId = resultSet.getString("target_id"),
      targetTitle = resultSet.getString("target_title"),
      targetPath = resultSet.getString("target_path"),
      body = resultSet.getString("body"),
      createdAt = resultSet.getLong("created_at"),
      mailId = resultSet.getString("mail_id")
    )
  }
}
