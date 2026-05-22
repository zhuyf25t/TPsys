package services.governance.database

import java.util.UUID

import services.governance.objects.{
  ContributionAdjustmentId,
  GovernanceMailSnapshotId,
  GovernanceReviewNotificationId
}

private[database] trait GovernanceIdGenerator {
  def nextAdjustmentId(): ContributionAdjustmentId
  def nextReviewIds(): GovernanceReviewGeneratedIds
}

private[database] object RandomGovernanceIdGenerator extends GovernanceIdGenerator {
  override def nextAdjustmentId(): ContributionAdjustmentId =
    ContributionAdjustmentId(s"governance-adjustment-${UUID.randomUUID().toString}")

  override def nextReviewIds(): GovernanceReviewGeneratedIds = {
    val id = UUID.randomUUID().toString
    GovernanceReviewGeneratedIds(
      notificationId = GovernanceReviewNotificationId(s"governance-review-$id"),
      mailId = GovernanceMailSnapshotId(s"mail-governance-review-$id")
    )
  }
}
