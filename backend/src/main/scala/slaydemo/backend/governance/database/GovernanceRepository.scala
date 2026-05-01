package slaydemo.backend.governance.database

import slaydemo.backend.governance.objects.{
  ContributionAdjustmentId,
  ContributionAdjustmentRecord,
  GovernanceMailSnapshotId,
  GovernanceReviewKind,
  GovernanceReviewNotificationId,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType
}

final case class GovernanceReviewGeneratedIds(
  notificationId: GovernanceReviewNotificationId,
  mailId: GovernanceMailSnapshotId
)

trait GovernanceRepository {
  def nextAdjustmentId(): ContributionAdjustmentId
  def listAdjustments(limit: Int): Vector[ContributionAdjustmentRecord]
  def saveAdjustment(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord
  def nextReviewIds(): GovernanceReviewGeneratedIds
  def listReviewNotifications(
    kind: Option[GovernanceReviewKind],
    targetType: Option[GovernanceReviewTargetType],
    limit: Int
  ): Vector[GovernanceReviewNotificationRecord]
  def saveReviewNotification(record: GovernanceReviewNotificationRecord): GovernanceReviewNotificationRecord
}
