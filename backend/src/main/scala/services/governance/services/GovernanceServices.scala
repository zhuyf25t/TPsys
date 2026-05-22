package services.governance.services

import services.battle.objects.EpochMillis
import services.governance.database.{GovernanceRepository, InMemoryGovernanceRepository}
import services.governance.objects.*
import services.identity.objects.PlayerHandle
import services.mail.database.{InMemoryMailRepository, MailRepository}
import services.mail.objects.{MailId, MailRecord}

final case class ContributionAdjustmentSubmissionResult(
  adjustment: ContributionAdjustmentRecord,
  mail: GovernanceMailSnapshot
)

final case class ContributionAdjustmentCommand(
  actorHandle: AdminHandle,
  targetHandle: GovernanceTargetHandle,
  delta: ContributionDelta,
  reason: GovernanceReason,
  sourceLabel: GovernanceSourceLabel,
  sourcePath: GovernanceSourcePath
)

final case class GovernanceReviewNotificationSubmissionResult(
  notification: GovernanceReviewNotificationRecord,
  mail: GovernanceMailSnapshot
)

final case class GovernanceReviewNotificationCommand(
  actorHandle: GovernanceActorHandle,
  kind: GovernanceReviewKind,
  targetType: GovernanceReviewTargetType,
  targetId: GovernanceReviewTargetId,
  targetTitle: GovernanceReviewTargetTitle,
  targetPath: GovernanceReviewTargetPath,
  body: GovernanceReviewBody
)

trait ContributionAdjustmentService {
  def list(limit: Int): Vector[ContributionAdjustmentRecord]

  def create(command: ContributionAdjustmentCommand): ContributionAdjustmentSubmissionResult
}

trait GovernanceNotificationService {
  def listReviewNotifications(
    kind: Option[GovernanceReviewKind],
    targetType: Option[GovernanceReviewTargetType],
    limit: Int
  ): Vector[GovernanceReviewNotificationRecord]

  def createReviewNotification(
    command: GovernanceReviewNotificationCommand
  ): GovernanceReviewNotificationSubmissionResult
}

final class DefaultGovernanceService(
  repository: GovernanceRepository,
  mailRepository: MailRepository,
  currentTimeMillis: () => Long
)
    extends ContributionAdjustmentService
    with GovernanceNotificationService {
  override def list(limit: Int): Vector[ContributionAdjustmentRecord] =
    repository.listAdjustments(clampLimit(limit, 1_000))

  override def create(
    command: ContributionAdjustmentCommand
  ): ContributionAdjustmentSubmissionResult = {
    val adjustment = ContributionAdjustmentRecord(
      id = repository.nextAdjustmentId(),
      actorHandle = command.actorHandle,
      targetHandle = command.targetHandle,
      delta = command.delta,
      reason = command.reason,
      createdAt = EpochMillis(currentTimeMillis()),
      sourceLabel = command.sourceLabel,
      sourcePath = command.sourcePath
    )
    val saved = repository.saveAdjustment(adjustment)
    val mail = GovernanceMailFactory.contributionMail(saved)
    persistMail(mail)
    ContributionAdjustmentSubmissionResult(saved, mail)
  }

  override def listReviewNotifications(
    kind: Option[GovernanceReviewKind],
    targetType: Option[GovernanceReviewTargetType],
    limit: Int
  ): Vector[GovernanceReviewNotificationRecord] =
    repository.listReviewNotifications(kind, targetType, clampLimit(limit, 1_000))

  override def createReviewNotification(
    command: GovernanceReviewNotificationCommand
  ): GovernanceReviewNotificationSubmissionResult = {
    val ids = repository.nextReviewIds()
    val notification = GovernanceReviewNotificationRecord(
      id = ids.notificationId,
      actorHandle = command.actorHandle,
      kind = command.kind,
      targetType = command.targetType,
      targetId = command.targetId,
      targetTitle = command.targetTitle,
      targetPath = command.targetPath,
      body = command.body,
      createdAt = EpochMillis(currentTimeMillis()),
      mailId = ids.mailId
    )
    val saved = repository.saveReviewNotification(notification)
    val mail = GovernanceMailFactory.reviewMail(saved)
    persistMail(mail)
    GovernanceReviewNotificationSubmissionResult(saved, mail)
  }

  private def persistMail(snapshot: GovernanceMailSnapshot): Unit =
    PlayerHandle.forLookup(snapshot.ownerHandle.value).foreach { ownerHandle =>
      mailRepository.save(
        MailRecord(
          id = MailId(snapshot.id.value),
          ownerHandle = ownerHandle,
          kind = snapshot.kind,
          subject = snapshot.subject,
          excerpt = snapshot.excerpt,
          senderLabel = snapshot.senderLabel,
          readState = snapshot.readState,
          importance = snapshot.importance,
          createdAt = snapshot.createdAt,
          sourceBattleId = None,
          sourcePath = None,
          sourceLabel = None,
          governanceMetadata = snapshot.governanceMetadata,
          friendRequestMetadata = None
        )
      )
    }

  private def clampLimit(value: Int, max: Int): Int =
    math.max(0, math.min(value, max))
}

object DefaultGovernanceService {
  def apply(
    repository: GovernanceRepository,
    mailRepository: MailRepository,
    currentTimeMillis: () => Long
  ): DefaultGovernanceService =
    new DefaultGovernanceService(repository, mailRepository, currentTimeMillis)
}

object InMemoryGovernanceService {
  def apply(): DefaultGovernanceService =
    DefaultGovernanceService(InMemoryGovernanceRepository(), InMemoryMailRepository(), () => System.currentTimeMillis())
}
