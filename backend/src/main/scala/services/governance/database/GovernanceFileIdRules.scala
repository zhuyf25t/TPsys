package services.governance.database

import services.governance.objects.{
  ContributionAdjustmentId,
  GovernanceMailSnapshotId,
  GovernanceReviewNotificationId
}

private[database] final case class GovernanceFileIdCounters(
  nextAdjustmentNumber: Long,
  nextReviewNumber: Long
) {
  def allocateAdjustmentId: (ContributionAdjustmentId, GovernanceFileIdCounters) =
    (
      ContributionAdjustmentId(f"governance-adjustment-$nextAdjustmentNumber%06d"),
      copy(nextAdjustmentNumber = nextAdjustmentNumber + 1L)
    )

  def allocateReviewIds: (GovernanceReviewGeneratedIds, GovernanceFileIdCounters) =
    (
      GovernanceReviewGeneratedIds(
        notificationId = GovernanceReviewNotificationId(f"governance-review-$nextReviewNumber%06d"),
        mailId = GovernanceMailSnapshotId(f"mail-governance-review-$nextReviewNumber%06d")
      ),
      copy(nextReviewNumber = nextReviewNumber + 1L)
    )

  def afterAdjustmentId(id: ContributionAdjustmentId): GovernanceFileIdCounters =
    copy(nextAdjustmentNumber = advance(nextAdjustmentNumber, id.value, "governance-adjustment-"))

  def afterReviewNotificationId(id: GovernanceReviewNotificationId): GovernanceFileIdCounters =
    copy(nextReviewNumber = advance(nextReviewNumber, id.value, "governance-review-"))

  def afterReviewMailId(id: GovernanceMailSnapshotId): GovernanceFileIdCounters =
    copy(nextReviewNumber = advance(nextReviewNumber, id.value, "mail-governance-review-"))

  private def advance(current: Long, value: String, prefix: String): Long =
    parseNumericId(value, prefix).map(number => math.max(current, number + 1L)).getOrElse(current)

  private def parseNumericId(value: String, prefix: String): Option[Long] = {
    val trimmed = value.trim
    Option.when(trimmed.startsWith(prefix) && trimmed.drop(prefix.length).forall(_.isDigit)) {
      trimmed.drop(prefix.length).toLong
    }
  }
}

private[database] object GovernanceFileIdCounters {
  val initial: GovernanceFileIdCounters =
    GovernanceFileIdCounters(
      nextAdjustmentNumber = 1L,
      nextReviewNumber = 1L
    )
}
