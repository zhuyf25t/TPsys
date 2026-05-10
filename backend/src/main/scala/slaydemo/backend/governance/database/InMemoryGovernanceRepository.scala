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

final class InMemoryGovernanceRepository extends GovernanceRepository {
  private val lock = Object()
  private var adjustmentsById: Map[ContributionAdjustmentId, ContributionAdjustmentRecord] = Map.empty
  private var notificationsById: Map[GovernanceReviewNotificationId, GovernanceReviewNotificationRecord] = Map.empty
  private var nextAdjustmentNumber: Long = 1L
  private var nextNotificationNumber: Long = 1L

  override def nextAdjustmentId(): ContributionAdjustmentId =
    lock.synchronized {
      val id = ContributionAdjustmentId(f"governance-adjustment-$nextAdjustmentNumber%06d")
      nextAdjustmentNumber += 1L
      id
    }

  override def listAdjustments(limit: Int): Vector[ContributionAdjustmentRecord] =
    lock.synchronized {
      adjustmentsById.values.toVector
    }.sortWith(GovernanceRepositoryOrderingRules.adjustmentsRecentFirst).take(math.max(0, limit))

  override def saveAdjustment(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord = {
    lock.synchronized {
      adjustmentsById = adjustmentsById.updated(record.id, record)
    }
    record
  }

  override def nextReviewIds(): GovernanceReviewGeneratedIds =
    lock.synchronized {
      val idNumber = nextNotificationNumber
      nextNotificationNumber += 1L
      GovernanceReviewGeneratedIds(
        notificationId = GovernanceReviewNotificationId(f"governance-review-$idNumber%06d"),
        mailId = GovernanceMailSnapshotId(f"mail-governance-review-$idNumber%06d")
      )
    }

  override def listReviewNotifications(
    kind: Option[GovernanceReviewKind],
    targetType: Option[GovernanceReviewTargetType],
    limit: Int
  ): Vector[GovernanceReviewNotificationRecord] =
    lock.synchronized {
      notificationsById.values.toVector
    }.filter(record => kind.forall(_ == record.kind))
      .filter(record => targetType.forall(_ == record.targetType))
      .sortWith(GovernanceRepositoryOrderingRules.notificationsRecentFirst)
      .take(math.max(0, limit))

  override def saveReviewNotification(
    record: GovernanceReviewNotificationRecord
  ): GovernanceReviewNotificationRecord = {
    lock.synchronized {
      notificationsById = notificationsById.updated(record.id, record)
    }
    record
  }
}

object InMemoryGovernanceRepository {
  def apply(): InMemoryGovernanceRepository =
    new InMemoryGovernanceRepository()
}
