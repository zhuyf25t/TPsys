package slaydemo.backend.governance.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

import slaydemo.backend.governance.objects.{
  ContributionAdjustmentRecord,
  GovernanceMailSnapshot,
  GovernanceReviewKind,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType
}
import slaydemo.backend.governance.services.{
  ContributionAdjustmentCommand,
  ContributionAdjustmentSubmissionResult,
  GovernanceReviewNotificationCommand,
  GovernanceReviewNotificationSubmissionResult
}
import slaydemo.backend.mail.objects.MailKind

object GovernanceRequestTarget {
  private val ContributionAdjustmentPaths: Set[String] =
    Set("/governance/contribution-adjustments", "/api/governance/contribution-adjustments")
  private val AdminNotificationPaths: Set[String] =
    Set("/governance/admin-notifications", "/api/governance/admin-notifications")

  def isContributionAdjustmentPath(path: String): Boolean =
    ContributionAdjustmentPaths.contains(path)

  def isAdminNotificationPath(path: String): Boolean =
    AdminNotificationPaths.contains(path)

  def contributionAdjustmentLimitFromQuery(query: Map[String, String]): Int =
    GovernanceQueryParsers.parseContributionAdjustmentLimit(query)

  def notificationListFromQuery(query: Map[String, String]): GovernanceNotificationListQueryParseResult =
    GovernanceQueryParsers.parseNotificationListQuery(query)
}

final case class ContributionAdjustmentRequest(
  actorHandle: String,
  targetHandle: String,
  delta: Int,
  reason: String,
  sourceLabel: String,
  sourcePath: String
)

final case class GovernanceReviewNotificationRequest(
  actorHandle: String,
  kind: String,
  targetType: String,
  targetId: String,
  targetTitle: String,
  targetPath: String,
  body: String
)

final case class ContributionAdjustmentApiRequest(
  actorHandle: String,
  targetHandle: String,
  delta: Int,
  reason: String,
  sourceLabel: String,
  sourcePath: String
) {
  def toCommandRequest: ContributionAdjustmentRequest =
    ContributionAdjustmentRequest(
      actorHandle = actorHandle,
      targetHandle = targetHandle,
      delta = delta,
      reason = reason,
      sourceLabel = sourceLabel,
      sourcePath = sourcePath
    )

  def toCommand: Either[ContributionAdjustmentCommandParseError, ContributionAdjustmentCommand] =
    GovernanceCommandParsers.parseContributionAdjustmentCommand(toCommandRequest)
}

object ContributionAdjustmentApiRequest {
  given Decoder[ContributionAdjustmentApiRequest] =
    Decoder.instance { cursor =>
      for {
        actorHandle <- requiredNonEmptyString(cursor, "actorHandle")
        targetHandle <- requiredNonEmptyString(cursor, "targetHandle")
        delta <- cursor.downField("delta").as[Int]
        reason <- optionalString(cursor, "reason")
        sourceLabel <- optionalString(cursor, "sourceLabel")
        sourcePath <- optionalString(cursor, "sourcePath")
      } yield ContributionAdjustmentApiRequest(actorHandle, targetHandle, delta, reason, sourceLabel, sourcePath)
    }
}

final case class GovernanceReviewNotificationApiRequest(
  actorHandle: String,
  kind: String,
  targetType: String,
  targetId: String,
  targetTitle: String,
  targetPath: String,
  body: String
) {
  def toCommandRequest: GovernanceReviewNotificationRequest =
    GovernanceReviewNotificationRequest(
      actorHandle = actorHandle,
      kind = kind,
      targetType = targetType,
      targetId = targetId,
      targetTitle = targetTitle,
      targetPath = targetPath,
      body = body
    )

  def toCommand: Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewNotificationCommand] =
    GovernanceCommandParsers.parseReviewNotificationCommand(toCommandRequest)
}

object GovernanceReviewNotificationApiRequest {
  given Decoder[GovernanceReviewNotificationApiRequest] =
    Decoder.instance { cursor =>
      for {
        actorHandle <- optionalString(cursor, "actorHandle")
        kind <- requiredNonEmptyString(cursor, "kind")
        targetType <- requiredNonEmptyString(cursor, "targetType")
        targetId <- requiredNonEmptyString(cursor, "targetId")
        targetTitle <- optionalString(cursor, "targetTitle")
        targetPath <- optionalString(cursor, "targetPath")
        body <- requiredNonEmptyString(cursor, "body")
      } yield GovernanceReviewNotificationApiRequest(
        actorHandle = actorHandle,
        kind = kind,
        targetType = targetType,
        targetId = targetId,
        targetTitle = targetTitle,
        targetPath = targetPath,
        body = body
      )
    }
}

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
    Encoder.instance { mail =>
      Json.obj(
        (
          Vector(
            "id" -> Json.fromString(mail.id),
            "ownerHandle" -> Json.fromString(mail.ownerHandle),
            "kind" -> Json.fromString(mail.kind),
            "subject" -> Json.fromString(mail.subject),
            "excerpt" -> Json.fromString(mail.excerpt),
            "senderLabel" -> Json.fromString(mail.senderLabel),
            "unread" -> Json.fromBoolean(mail.unread),
            "important" -> Json.fromBoolean(mail.important),
            "createdAt" -> Json.fromLong(mail.createdAt)
          ) ++ optionalStringField("governanceActorHandle", mail.governanceActorHandle) ++
            optionalStringField("governanceTargetPath", mail.governanceTargetPath) ++
            optionalStringField("governanceTargetLabel", mail.governanceTargetLabel)
        )*
      )
    }

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

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, Json)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> Json.fromString(text))).getOrElse(Vector.empty)
}

final case class ContributionAdjustmentListResponse(adjustments: Vector[ContributionAdjustmentItemResponse])

object ContributionAdjustmentListResponse {
  given Encoder[ContributionAdjustmentListResponse] =
    Encoder.forProduct1("adjustments")(_.adjustments)

  def fromRecords(records: Vector[ContributionAdjustmentRecord]): ContributionAdjustmentListResponse =
    ContributionAdjustmentListResponse(records.map(ContributionAdjustmentItemResponse.fromRecord))
}

final case class ContributionAdjustmentCreateResponse(
  ok: Boolean,
  adjustment: ContributionAdjustmentItemResponse,
  mail: GovernanceMailSnapshotResponse
)

object ContributionAdjustmentCreateResponse {
  given Encoder[ContributionAdjustmentCreateResponse] =
    Encoder.forProduct3("ok", "adjustment", "mail")(value => (value.ok, value.adjustment, value.mail))

  def fromResult(result: ContributionAdjustmentSubmissionResult): ContributionAdjustmentCreateResponse =
    ContributionAdjustmentCreateResponse(
      ok = true,
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
  ok: Boolean,
  notification: GovernanceReviewNotificationItemResponse,
  mail: GovernanceMailSnapshotResponse
)

object GovernanceReviewNotificationCreateResponse {
  given Encoder[GovernanceReviewNotificationCreateResponse] =
    Encoder.forProduct3("ok", "notification", "mail")(value => (value.ok, value.notification, value.mail))

  def fromResult(result: GovernanceReviewNotificationSubmissionResult): GovernanceReviewNotificationCreateResponse =
    GovernanceReviewNotificationCreateResponse(
      ok = true,
      notification = GovernanceReviewNotificationItemResponse.fromRecord(result.notification),
      mail = GovernanceMailSnapshotResponse.fromSnapshot(result.mail)
    )
}

private def requiredNonEmptyString(cursor: HCursor, field: String): Decoder.Result[String] =
  cursor.downField(field).as[String].flatMap { value =>
    Either.cond(
      Option(value).exists(_.trim.nonEmpty),
      value,
      DecodingFailure(s"$field must be non-empty", cursor.history)
    )
  }

private def optionalString(cursor: HCursor, field: String): Decoder.Result[String] =
  cursor.downField(field).as[Option[String]].map(_.getOrElse(""))
