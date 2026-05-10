package slaydemo.backend.governance.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.governance.objects.{
  ContributionAdjustmentId,
  ContributionAdjustmentRecord,
  GovernanceReviewKind,
  GovernanceReviewNotificationId,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType,
}
import slaydemo.backend.shared.database.AtomicFileWrite

final class FileGovernanceRepository(
  adjustmentsPath: Path,
  reviewNotificationsPath: Path
) extends GovernanceRepository {
  private val lock = Object()
  private var adjustmentsById: Map[ContributionAdjustmentId, ContributionAdjustmentRecord] = Map.empty
  private var notificationsById: Map[GovernanceReviewNotificationId, GovernanceReviewNotificationRecord] = Map.empty
  private var idCounters: GovernanceFileIdCounters = GovernanceFileIdCounters.initial

  loadAdjustmentsFromDisk()
  loadReviewNotificationsFromDisk()

  override def nextAdjustmentId(): ContributionAdjustmentId =
    lock.synchronized {
      val (id, nextCounters) = idCounters.allocateAdjustmentId
      idCounters = nextCounters
      id
    }

  override def listAdjustments(limit: Int): Vector[ContributionAdjustmentRecord] =
    lock.synchronized {
      adjustmentsById.values.toVector
    }.sortWith(GovernanceRepositoryOrderingRules.adjustmentsRecentFirst)
      .take(math.max(0, limit))

  override def saveAdjustment(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord = {
    lock.synchronized {
      adjustmentsById = adjustmentsById.updated(record.id, record)
      idCounters = idCounters.afterAdjustmentId(record.id)
      persistAdjustments()
    }
    record
  }

  override def nextReviewIds(): GovernanceReviewGeneratedIds =
    lock.synchronized {
      val (ids, nextCounters) = idCounters.allocateReviewIds
      idCounters = nextCounters
      ids
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
      idCounters = idCounters
        .afterReviewNotificationId(record.id)
        .afterReviewMailId(record.mailId)
      persistReviewNotifications()
    }
    record
  }

  private def loadAdjustmentsFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(adjustmentsPath) then {
        val raw = Files.readString(adjustmentsPath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          adjustmentsById = GovernanceFileJsonParser.parseAdjustments(raw)
            .map(record => record.id -> record)
            .toMap
          adjustmentsById.keys.foreach(id => idCounters = idCounters.afterAdjustmentId(id))
        }
      }
    }

  private def loadReviewNotificationsFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(reviewNotificationsPath) then {
        val raw = Files.readString(reviewNotificationsPath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          notificationsById = GovernanceFileJsonParser.parseReviewNotifications(raw)
            .map(record => record.id -> record)
            .toMap
          notificationsById.values.foreach { record =>
            idCounters = idCounters
              .afterReviewNotificationId(record.id)
              .afterReviewMailId(record.mailId)
          }
        }
      }
    }

  private def persistAdjustments(): Unit = {
    val payload = GovernanceFileJsonRenderer.renderAdjustmentsPayload(
      adjustmentsById.values.toVector.sortWith(GovernanceRepositoryOrderingRules.adjustmentsRecentFirst)
    )
    writeAtomic(adjustmentsPath, payload)
  }

  private def persistReviewNotifications(): Unit = {
    val payload = GovernanceFileJsonRenderer.renderNotificationsPayload(
      notificationsById.values.toVector.sortWith(GovernanceRepositoryOrderingRules.notificationsRecentFirst)
    )
    writeAtomic(reviewNotificationsPath, payload)
  }

  private def writeAtomic(path: Path, payload: String): Unit =
    AtomicFileWrite.writeUtf8(path, payload)

}

object FileGovernanceRepository {
  def apply(root: Path): FileGovernanceRepository =
    new FileGovernanceRepository(
      adjustmentsPath = root.resolve("governance-contribution-adjustments.json"),
      reviewNotificationsPath = root.resolve("governance-review-notifications.json")
    )
}
