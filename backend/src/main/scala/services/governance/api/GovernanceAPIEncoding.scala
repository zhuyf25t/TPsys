package services.governance.api

import io.circe.Encoder

import services.governance.objects.{
  ContributionAdjustmentRecord,
  GovernanceMailSnapshot,
  GovernanceReviewKind,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType
}
import services.governance.services.{
  ContributionAdjustmentSubmissionResult,
  GovernanceReviewNotificationSubmissionResult
}
import services.mail.objects.MailKind

final case class ContributionAdjustmentItemResponse(
  id: String,
  actorHandle: String,
  targetHandle: String,
  delta: Int,
  reason: String,
  createdAt: Long,
  sourceLabel: String,
  sourcePath: String
)

object ContributionAdjustmentItemResponse {
  given Encoder[ContributionAdjustmentItemResponse] =
    Encoder.forProduct8(
      "id",
      "actorHandle",
      "targetHandle",
      "delta",
      "reason",
      "createdAt",
      "sourceLabel",
      "sourcePath"
    )(value =>
      (
        value.id,
        value.actorHandle,
        value.targetHandle,
        value.delta,
        value.reason,
        value.createdAt,
        value.sourceLabel,
        value.sourcePath
      )
    )

  def fromRecord(record: ContributionAdjustmentRecord): ContributionAdjustmentItemResponse =
    ContributionAdjustmentItemResponse(
      id = record.id.value,
      actorHandle = record.actorHandle.value,
      targetHandle = record.targetHandle.value,
      delta = record.delta.value,
      reason = record.reason.value,
      createdAt = record.createdAt.value,
      sourceLabel = record.sourceLabel.value,
      sourcePath = record.sourcePath.value
    )
}

final case class GovernanceReviewNotificationItemResponse(
  id: String,
  actorHandle: String,
  kind: String,
  targetType: String,
  targetId: String,
  targetTitle: String,
  targetPath: String,
  body: String,
  createdAt: Long,
  mailId: String
)

object GovernanceReviewNotificationItemResponse {
  given Encoder[GovernanceReviewNotificationItemResponse] =
    Encoder.forProduct10(
      "id",
      "actorHandle",
      "kind",
      "targetType",
      "targetId",
      "targetTitle",
      "targetPath",
      "body",
      "createdAt",
      "mailId"
    )(value =>
      (
        value.id,
        value.actorHandle,
        value.kind,
        value.targetType,
        value.targetId,
        value.targetTitle,
        value.targetPath,
        value.body,
        value.createdAt,
        value.mailId
      )
    )

  def fromRecord(record: GovernanceReviewNotificationRecord): GovernanceReviewNotificationItemResponse =
    GovernanceReviewNotificationItemResponse(
      id = record.id.value,
      actorHandle = record.actorHandle.value,
      kind = GovernanceReviewKind.wireValue(record.kind),
      targetType = GovernanceReviewTargetType.wireValue(record.targetType),
      targetId = record.targetId.value,
      targetTitle = record.targetTitle.value,
      targetPath = record.targetPath.value,
      body = record.body.value,
      createdAt = record.createdAt.value,
      mailId = record.mailId.value
    )
}

final case class GovernanceMailSnapshotResponse(
  id: String,
  ownerHandle: String,
  kind: String,
  subject: String,
  excerpt: String,
  senderLabel: String,
  unread: Boolean,
  important: Boolean,
  createdAt: Long,
  governanceActorHandle: Option[String],
  governanceTargetPath: Option[String],
  governanceTargetLabel: Option[String]
)

object GovernanceMailSnapshotResponse {
  given Encoder[GovernanceMailSnapshotResponse] =
    Encoder
      .forProduct12(
        "id",
        "ownerHandle",
        "kind",
        "subject",
        "excerpt",
        "senderLabel",
        "unread",
        "important",
        "createdAt",
        "governanceActorHandle",
        "governanceTargetPath",
        "governanceTargetLabel"
      )((mail: GovernanceMailSnapshotResponse) =>
        (
          mail.id,
          mail.ownerHandle,
          mail.kind,
          mail.subject,
          mail.excerpt,
          mail.senderLabel,
          mail.unread,
          mail.important,
          mail.createdAt,
          optionalString(mail.governanceActorHandle),
          optionalString(mail.governanceTargetPath),
          optionalString(mail.governanceTargetLabel)
        )
      )
      .mapJson(_.dropNullValues)

  def fromSnapshot(snapshot: GovernanceMailSnapshot): GovernanceMailSnapshotResponse =
    GovernanceMailSnapshotResponse(
      id = snapshot.id.value,
      ownerHandle = snapshot.ownerHandle.value,
      kind = MailKind.wireValue(snapshot.kind),
      subject = snapshot.subject,
      excerpt = snapshot.excerpt,
      senderLabel = snapshot.senderLabel,
      unread = snapshot.unread,
      important = snapshot.important,
      createdAt = snapshot.createdAt.value,
      governanceActorHandle = snapshot.governanceMetadata.map(_.actorHandle.value),
      governanceTargetPath = snapshot.governanceMetadata.map(_.targetPath.value),
      governanceTargetLabel = snapshot.governanceMetadata.map(_.targetLabel.value)
    )

  private def optionalString(value: Option[String]): Option[String] =
    value.filter(_.trim.nonEmpty)
}

final case class ContributionAdjustmentListResponse(adjustments: Vector[ContributionAdjustmentItemResponse])

object ContributionAdjustmentListResponse {
  given Encoder[ContributionAdjustmentListResponse] =
    Encoder.forProduct1("adjustments")(_.adjustments)

  def fromRecords(records: Vector[ContributionAdjustmentRecord]): ContributionAdjustmentListResponse =
    ContributionAdjustmentListResponse(records.map(ContributionAdjustmentItemResponse.fromRecord))
}

enum GovernanceSubmissionApiOutcome {
  case Submitted
}

object GovernanceSubmissionApiOutcome {
  def okFlag(outcome: GovernanceSubmissionApiOutcome): Boolean =
    outcome match {
      case GovernanceSubmissionApiOutcome.Submitted => true
    }
}

final case class ContributionAdjustmentCreateResponse(
  outcome: GovernanceSubmissionApiOutcome,
  adjustment: ContributionAdjustmentItemResponse,
  mail: GovernanceMailSnapshotResponse
)

object ContributionAdjustmentCreateResponse {
  given Encoder[ContributionAdjustmentCreateResponse] =
    Encoder.forProduct3("ok", "adjustment", "mail")(value =>
      (GovernanceSubmissionApiOutcome.okFlag(value.outcome), value.adjustment, value.mail)
    )

  def fromResult(result: ContributionAdjustmentSubmissionResult): ContributionAdjustmentCreateResponse =
    ContributionAdjustmentCreateResponse(
      outcome = GovernanceSubmissionApiOutcome.Submitted,
      adjustment = ContributionAdjustmentItemResponse.fromRecord(result.adjustment),
      mail = GovernanceMailSnapshotResponse.fromSnapshot(result.mail)
    )
}

final case class GovernanceReviewNotificationListResponse(
  notifications: Vector[GovernanceReviewNotificationItemResponse]
)

object GovernanceReviewNotificationListResponse {
  given Encoder[GovernanceReviewNotificationListResponse] =
    Encoder.forProduct1("notifications")(_.notifications)

  def fromRecords(records: Vector[GovernanceReviewNotificationRecord]): GovernanceReviewNotificationListResponse =
    GovernanceReviewNotificationListResponse(records.map(GovernanceReviewNotificationItemResponse.fromRecord))
}

final case class GovernanceReviewNotificationCreateResponse(
  outcome: GovernanceSubmissionApiOutcome,
  notification: GovernanceReviewNotificationItemResponse,
  mail: GovernanceMailSnapshotResponse
)

object GovernanceReviewNotificationCreateResponse {
  given Encoder[GovernanceReviewNotificationCreateResponse] =
    Encoder.forProduct3("ok", "notification", "mail")(value =>
      (GovernanceSubmissionApiOutcome.okFlag(value.outcome), value.notification, value.mail)
    )

  def fromResult(result: GovernanceReviewNotificationSubmissionResult): GovernanceReviewNotificationCreateResponse =
    GovernanceReviewNotificationCreateResponse(
      outcome = GovernanceSubmissionApiOutcome.Submitted,
      notification = GovernanceReviewNotificationItemResponse.fromRecord(result.notification),
      mail = GovernanceMailSnapshotResponse.fromSnapshot(result.mail)
    )
}
