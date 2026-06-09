package services.governance.services

import services.governance.objects.*
import services.mail.objects.{
  GovernanceMailActorHandle,
  GovernanceMailMetadata,
  GovernanceMailTargetLabel,
  GovernanceMailTargetPath,
  MailImportance,
  MailKind,
  MailReadState
}

private[services] object GovernanceMailFactory {
  def contributionMail(record: ContributionAdjustmentRecord): GovernanceMailSnapshot =
    GovernanceMailSnapshot(
      id = GovernanceMailSnapshotId(s"mail-${record.id.value}"),
      ownerHandle = record.targetHandle,
      kind = MailKind.Governance,
      subject = s"Contribution adjustment ${formatDelta(record.delta)}",
      excerpt = contributionMailExcerpt(record),
      senderLabel = s"Admin @${record.actorHandle.value}",
      readState = MailReadState.Unread,
      importance = MailImportance.Important,
      createdAt = record.createdAt,
      governanceMetadata = None
    )

  def reviewMail(record: GovernanceReviewNotificationRecord): GovernanceMailSnapshot =
    GovernanceMailSnapshot(
      id = record.mailId,
      ownerHandle = GovernanceTargetHandle("admin"),
      kind = MailKind.Governance,
      subject = s"[Review] ${GovernanceReviewKind.displayLabel(record.kind)}: ${reviewTargetLabel(record).take(36)}",
      excerpt = reviewMailExcerpt(record),
      senderLabel = s"Governance notice @${record.actorHandle.value}",
      readState = MailReadState.Unread,
      importance = MailImportance.Important,
      createdAt = record.createdAt,
      governanceMetadata = Some(
        GovernanceMailMetadata(
          actorHandle = GovernanceMailActorHandle(record.actorHandle.value),
          targetPath = GovernanceMailTargetPath(record.targetPath.value),
          targetLabel = GovernanceMailTargetLabel(reviewTargetLabel(record))
        )
      )
    )

  private def contributionMailExcerpt(record: ContributionAdjustmentRecord): String = {
    val reason = if record.reason.value.isEmpty then "" else s" Reason: ${record.reason.value}"
    val source = (record.sourceLabel.value, record.sourcePath.value) match {
      case ("", "")      => ""
      case (label, "")   => s" Source: $label"
      case ("", path)    => s" Source: $path"
      case (label, path) => s" Source: $label $path"
    }
    s"@${record.actorHandle.value} adjusted your contribution by ${formatDelta(record.delta)}.$reason$source"
  }

  private def reviewMailExcerpt(record: GovernanceReviewNotificationRecord): String = {
    val source = reviewSourceText(record)
    val targetId = if record.targetId.value.isEmpty then "" else s" Target id: ${record.targetId.value}."
    s"@${record.actorHandle.value} submitted ${GovernanceReviewKind.displayLabel(record.kind)}. Source: $source.$targetId Body: ${record.body.value}"
  }

  private def reviewTargetLabel(record: GovernanceReviewNotificationRecord): String =
    if record.targetTitle.value.trim.nonEmpty then record.targetTitle.value else record.targetId.value

  private def reviewSourceText(record: GovernanceReviewNotificationRecord): String = {
    val targetType = GovernanceReviewTargetType.wireValue(record.targetType)
    val label = reviewTargetLabel(record)
    val path = record.targetPath.value.trim
    Vector(targetType, label, path).filter(_.nonEmpty).mkString(" / ")
  }

  private def formatDelta(delta: ContributionDelta): String =
    if delta.value > 0 then s"+${delta.value}" else delta.value.toString
}
