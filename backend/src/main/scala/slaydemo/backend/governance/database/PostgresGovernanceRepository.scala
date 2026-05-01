package slaydemo.backend.governance.database

import java.sql.{PreparedStatement, ResultSet, Types}
import java.util.UUID

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.governance.objects.{
  AdminHandle,
  ContributionAdjustmentId,
  ContributionAdjustmentRecord,
  ContributionDelta,
  GovernanceActorHandle,
  GovernanceMailSnapshotId,
  GovernanceReason,
  GovernanceReviewBody,
  GovernanceReviewKind,
  GovernanceReviewNotificationId,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetId,
  GovernanceReviewTargetPath,
  GovernanceReviewTargetTitle,
  GovernanceReviewTargetType,
  GovernanceSourceLabel,
  GovernanceSourcePath,
  GovernanceTargetHandle
}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresGovernanceRepository(settings: PostgresConnectionSettings) extends GovernanceRepository {
  initialize()

  override def nextAdjustmentId(): ContributionAdjustmentId =
    ContributionAdjustmentId(s"governance-adjustment-${UUID.randomUUID().toString}")

  override def listAdjustments(limit: Int): Vector[ContributionAdjustmentRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT id, actor_handle, target_handle, delta, reason, created_at, source_label, source_path
          |FROM governance_contribution_adjustments
          |ORDER BY created_at DESC, id ASC
          |LIMIT ?""".stripMargin
      ) { statement =>
        statement.setInt(1, math.max(0, limit))
        PostgresSupport.withResultSet(statement)(readAdjustments)
      }
    }

  override def saveAdjustment(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO governance_contribution_adjustments (
          |  id, actor_handle, target_handle, delta, reason, created_at, source_label, source_path
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (id) DO UPDATE SET
          |  actor_handle = EXCLUDED.actor_handle,
          |  target_handle = EXCLUDED.target_handle,
          |  delta = EXCLUDED.delta,
          |  reason = EXCLUDED.reason,
          |  created_at = EXCLUDED.created_at,
          |  source_label = EXCLUDED.source_label,
          |  source_path = EXCLUDED.source_path""".stripMargin
      ) { statement =>
        statement.setString(1, record.id.value)
        statement.setString(2, record.actorHandle.value)
        statement.setString(3, record.targetHandle.value)
        statement.setInt(4, record.delta.value)
        statement.setString(5, record.reason.value)
        statement.setLong(6, record.createdAt.value)
        statement.setString(7, record.sourceLabel.value)
        statement.setString(8, record.sourcePath.value)
        statement.executeUpdate()
      }
    }
    record
  }

  override def nextReviewIds(): GovernanceReviewGeneratedIds = {
    val id = UUID.randomUUID().toString
    GovernanceReviewGeneratedIds(
      notificationId = GovernanceReviewNotificationId(s"governance-review-$id"),
      mailId = GovernanceMailSnapshotId(s"mail-governance-review-$id")
    )
  }

  override def listReviewNotifications(
    kind: Option[GovernanceReviewKind],
    targetType: Option[GovernanceReviewTargetType],
    limit: Int
  ): Vector[GovernanceReviewNotificationRecord] =
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """SELECT id, actor_handle, kind, target_type, target_id, target_title, target_path, body, created_at, mail_id
          |FROM governance_review_notifications
          |WHERE (? IS NULL OR kind = ?) AND (? IS NULL OR target_type = ?)
          |ORDER BY created_at DESC, id ASC
          |LIMIT ?""".stripMargin
      ) { statement =>
        bindOptionalString(statement, 1, kind.map(GovernanceReviewKind.wireValue))
        bindOptionalString(statement, 3, targetType.map(GovernanceReviewTargetType.wireValue))
        statement.setInt(5, math.max(0, limit))
        PostgresSupport.withResultSet(statement)(readNotifications)
      }
    }

  override def saveReviewNotification(
    record: GovernanceReviewNotificationRecord
  ): GovernanceReviewNotificationRecord = {
    PostgresSupport.withConnection(settings) { connection =>
      PostgresSupport.withStatement(
        connection,
        """INSERT INTO governance_review_notifications (
          |  id, actor_handle, kind, target_type, target_id, target_title, target_path, body, created_at, mail_id
          |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT (id) DO UPDATE SET
          |  actor_handle = EXCLUDED.actor_handle,
          |  kind = EXCLUDED.kind,
          |  target_type = EXCLUDED.target_type,
          |  target_id = EXCLUDED.target_id,
          |  target_title = EXCLUDED.target_title,
          |  target_path = EXCLUDED.target_path,
          |  body = EXCLUDED.body,
          |  created_at = EXCLUDED.created_at,
          |  mail_id = EXCLUDED.mail_id""".stripMargin
      ) { statement =>
        statement.setString(1, record.id.value)
        statement.setString(2, record.actorHandle.value)
        statement.setString(3, GovernanceReviewKind.wireValue(record.kind))
        statement.setString(4, GovernanceReviewTargetType.wireValue(record.targetType))
        statement.setString(5, record.targetId.value)
        statement.setString(6, record.targetTitle.value)
        statement.setString(7, record.targetPath.value)
        statement.setString(8, record.body.value)
        statement.setLong(9, record.createdAt.value)
        statement.setString(10, record.mailId.value)
        statement.executeUpdate()
      }
    }
    record
  }

  private def initialize(): Unit =
    PostgresSupport.withConnection(settings) { connection =>
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
        "CREATE INDEX IF NOT EXISTS governance_contribution_adjustments_created_at_idx ON governance_contribution_adjustments (created_at DESC)"
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
    }

  private def bindOptionalString(statement: PreparedStatement, index: Int, value: Option[String]): Unit =
    value.map(_.trim).filter(_.nonEmpty) match {
      case Some(text) =>
        statement.setString(index, text)
        statement.setString(index + 1, text)
      case None =>
        statement.setNull(index, Types.VARCHAR)
        statement.setNull(index + 1, Types.VARCHAR)
    }

  private def readAdjustments(resultSet: ResultSet): Vector[ContributionAdjustmentRecord] = {
    val records = Vector.newBuilder[ContributionAdjustmentRecord]
    while resultSet.next() do {
      records += ContributionAdjustmentRecord(
        id = ContributionAdjustmentId(resultSet.getString("id")),
        actorHandle = AdminHandle(resultSet.getString("actor_handle")),
        targetHandle = GovernanceTargetHandle(resultSet.getString("target_handle")),
        delta = ContributionDelta(resultSet.getInt("delta")),
        reason = GovernanceReason(resultSet.getString("reason")),
        createdAt = EpochMillis(resultSet.getLong("created_at")),
        sourceLabel = GovernanceSourceLabel(Option(resultSet.getString("source_label")).getOrElse("")),
        sourcePath = GovernanceSourcePath(Option(resultSet.getString("source_path")).getOrElse(""))
      )
    }
    records.result()
  }

  private def readNotifications(resultSet: ResultSet): Vector[GovernanceReviewNotificationRecord] = {
    val records = Vector.newBuilder[GovernanceReviewNotificationRecord]
    while resultSet.next() do {
      records += GovernanceReviewNotificationRecord(
        id = GovernanceReviewNotificationId(resultSet.getString("id")),
        actorHandle = GovernanceActorHandle(resultSet.getString("actor_handle")),
        kind = readReviewKind(resultSet.getString("kind")),
        targetType = readReviewTargetType(resultSet.getString("target_type")),
        targetId = GovernanceReviewTargetId(resultSet.getString("target_id")),
        targetTitle = GovernanceReviewTargetTitle(resultSet.getString("target_title")),
        targetPath = GovernanceReviewTargetPath(resultSet.getString("target_path")),
        body = GovernanceReviewBody(resultSet.getString("body")),
        createdAt = EpochMillis(resultSet.getLong("created_at")),
        mailId = GovernanceMailSnapshotId(resultSet.getString("mail_id"))
      )
    }
    records.result()
  }

  private def readReviewKind(value: String): GovernanceReviewKind =
    GovernanceReviewKind.fromWire(value).getOrElse {
      val rendered = Option(value).getOrElse("<null>")
      throw new IllegalStateException(s"Invalid governance review kind in database: $rendered")
    }

  private def readReviewTargetType(value: String): GovernanceReviewTargetType =
    GovernanceReviewTargetType.fromWire(value).getOrElse {
      val rendered = Option(value).getOrElse("<null>")
      throw new IllegalStateException(s"Invalid governance review target type in database: $rendered")
    }
}
