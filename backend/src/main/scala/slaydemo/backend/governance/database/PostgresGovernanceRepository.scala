package slaydemo.backend.governance.database

import slaydemo.backend.governance.objects.{
  ContributionAdjustmentId,
  ContributionAdjustmentRecord,
  GovernanceReviewKind,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType
}
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.storage.PostgresConnectionSettings

final class PostgresGovernanceRepository(
  settings: PostgresConnectionSettings,
  idGenerator: GovernanceIdGenerator = RandomGovernanceIdGenerator
) extends GovernanceRepository {
  PostgresGovernanceSchema.initialize(settings)

  override def nextAdjustmentId(): ContributionAdjustmentId =
    idGenerator.nextAdjustmentId()

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
        PostgresSupport.withResultSet(statement)(PostgresGovernanceRecordMapper.readAdjustments)
      }
    }

  override def saveAdjustment(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord = {
    PostgresSupport.withTransactionConnection(settings) { connection =>
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
        PostgresGovernanceRecordMapper.bindAdjustment(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }

  override def nextReviewIds(): GovernanceReviewGeneratedIds =
    idGenerator.nextReviewIds()

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
        PostgresGovernanceRecordMapper.bindOptionalStringPair(statement, 1, kind.map(GovernanceReviewKind.wireValue))
        PostgresGovernanceRecordMapper.bindOptionalStringPair(statement, 3, targetType.map(GovernanceReviewTargetType.wireValue))
        statement.setInt(5, math.max(0, limit))
        PostgresSupport.withResultSet(statement)(PostgresGovernanceRecordMapper.readNotifications)
      }
    }

  override def saveReviewNotification(
    record: GovernanceReviewNotificationRecord
  ): GovernanceReviewNotificationRecord = {
    PostgresSupport.withTransactionConnection(settings) { connection =>
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
        PostgresGovernanceRecordMapper.bindReviewNotification(statement, record)
        statement.executeUpdate()
      }
    }
    record
  }
}
