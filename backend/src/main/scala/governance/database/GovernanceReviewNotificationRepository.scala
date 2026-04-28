package slaydemo.backend.governance.database

import slaydemo.backend.governance.objects.GovernanceReviewNotificationRecord

trait GovernanceReviewNotificationRepository {
  def list(kind: Option[String], targetType: Option[String], limit: Int): Seq[GovernanceReviewNotificationRecord]
  def findByMailId(mailId: String): Option[GovernanceReviewNotificationRecord]
  def save(record: GovernanceReviewNotificationRecord): GovernanceReviewNotificationRecord
}
