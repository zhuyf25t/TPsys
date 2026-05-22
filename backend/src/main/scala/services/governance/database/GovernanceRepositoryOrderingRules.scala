package services.governance.database

import services.governance.objects.{ContributionAdjustmentRecord, GovernanceReviewNotificationRecord}

private[database] object GovernanceRepositoryOrderingRules {
  def adjustmentsRecentFirst(left: ContributionAdjustmentRecord, right: ContributionAdjustmentRecord): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
    else left.id.value < right.id.value

  def notificationsRecentFirst(
    left: GovernanceReviewNotificationRecord,
    right: GovernanceReviewNotificationRecord
  ): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
    else left.id.value < right.id.value
}
