package slaydemo.backend.governance.services

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.governance.database.{GovernanceRepository, InMemoryGovernanceRepository}
import slaydemo.backend.governance.objects.*
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.{InMemoryMailRepository, MailRepository}
import slaydemo.backend.mail.objects.{GovernanceMailMetadata, MailId, MailKind, MailRecord}

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
    val mail = buildContributionMail(saved)
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
    val mail = buildReviewMail(saved)
    persistMail(mail)
    GovernanceReviewNotificationSubmissionResult(saved, mail)
  }

  private def buildContributionMail(record: ContributionAdjustmentRecord): GovernanceMailSnapshot =
    GovernanceMailSnapshot(
      id = GovernanceMailSnapshotId(s"mail-${record.id.value}"),
      ownerHandle = record.targetHandle,
      kind = MailKind.Governance,
      subject = s"Contribution adjustment ${formatDelta(record.delta)}",
      excerpt = contributionMailExcerpt(record),
      senderLabel = s"Admin @${record.actorHandle.value}",
      unread = true,
      important = true,
      createdAt = record.createdAt
    )

  private def buildReviewMail(record: GovernanceReviewNotificationRecord): GovernanceMailSnapshot =
    GovernanceMailSnapshot(
      id = record.mailId,
      ownerHandle = GovernanceTargetHandle("admin"),
      kind = MailKind.Governance,
      subject = s"[Review] ${GovernanceReviewKind.displayLabel(record.kind)}: ${reviewTargetLabel(record).take(36)}",
      excerpt = reviewMailExcerpt(record),
      senderLabel = s"Governance notice @${record.actorHandle.value}",
      unread = true,
      important = true,
      createdAt = record.createdAt,
      governanceMetadata = Some(
        GovernanceMailMetadata(
          actorHandle = record.actorHandle.value,
          targetPath = record.targetPath.value,
          targetLabel = reviewTargetLabel(record)
        )
      )
    )

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
          unread = snapshot.unread,
          important = snapshot.important,
          createdAt = snapshot.createdAt,
          governanceMetadata = snapshot.governanceMetadata
        )
      )
    }

  private def contributionMailExcerpt(record: ContributionAdjustmentRecord): String = {
    val reason = if record.reason.value.isEmpty then "" else s" Reason: ${record.reason.value}"
    val source = (record.sourceLabel.value, record.sourcePath.value) match {
      case ("", "")       => ""
      case (label, "")    => s" Source: $label"
      case ("", path)     => s" Source: $path"
      case (label, path)  => s" Source: $label $path"
    }
    s"@${record.actorHandle.value} adjusted your contribution by ${formatDelta(record.delta)}.$reason$source"
  }

  private def reviewMailExcerpt(record: GovernanceReviewNotificationRecord): String = {
    val targetType = GovernanceReviewTargetType.wireValue(record.targetType)
    val path = if record.targetPath.value.isEmpty then "" else s" Path: ${record.targetPath.value}."
    s"@${record.actorHandle.value} submitted ${GovernanceReviewKind.displayLabel(record.kind)} for ${reviewTargetLabel(record)} ($targetType:${record.targetId.value}). $path Body: ${record.body.value}"
  }

  private def reviewTargetLabel(record: GovernanceReviewNotificationRecord): String =
    if record.targetTitle.value.trim.nonEmpty then record.targetTitle.value else record.targetId.value

  private def formatDelta(delta: ContributionDelta): String =
    if delta.value > 0 then s"+${delta.value}" else delta.value.toString

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
