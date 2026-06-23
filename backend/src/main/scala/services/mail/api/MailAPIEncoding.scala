package services.mail.api

import io.circe.Encoder

import services.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}

final case class MailItemResponse(
  id: String,
  ownerHandle: String,
  kind: MailKind,
  subject: String,
  excerpt: String,
  senderLabel: String,
  unread: Boolean,
  important: Boolean,
  createdAt: Long,
  sourceBattleId: Option[String],
  sourcePath: Option[String],
  sourceLabel: Option[String],
  friendRequestId: Option[String],
  friendRequestStatus: Option[MailFriendRequestStatus],
  friendRequestSourceHandle: Option[String],
  governanceActorHandle: Option[String],
  governanceTargetPath: Option[String],
  governanceTargetLabel: Option[String]
)

object MailItemResponse {
  given Encoder[MailItemResponse] =
    Encoder
      .forProduct18(
        "id",
        "ownerHandle",
        "kind",
        "subject",
        "excerpt",
        "senderLabel",
        "unread",
        "important",
        "createdAt",
        "sourceBattleId",
        "sourcePath",
        "sourceLabel",
        "friendRequestId",
        "friendRequestStatus",
        "friendRequestSourceHandle",
        "governanceActorHandle",
        "governanceTargetPath",
        "governanceTargetLabel"
      )((item: MailItemResponse) =>
        (
          item.id,
          item.ownerHandle,
          MailKind.wireValue(item.kind),
          item.subject,
          item.excerpt,
          item.senderLabel,
          item.unread,
          item.important,
          item.createdAt,
          optionalString(item.sourceBattleId),
          optionalString(item.sourcePath),
          optionalString(item.sourceLabel),
          optionalString(item.friendRequestId),
          item.friendRequestStatus.map(MailFriendRequestStatus.wireValue),
          optionalString(item.friendRequestSourceHandle),
          optionalString(item.governanceActorHandle),
          optionalString(item.governanceTargetPath),
          optionalString(item.governanceTargetLabel)
        )
      )
      .mapJson(_.dropNullValues)

  def fromRecord(record: MailRecord): MailItemResponse =
    MailItemResponse(
      id = record.id.value,
      ownerHandle = record.ownerHandle.value,
      kind = record.kind,
      subject = record.subject,
      excerpt = record.excerpt,
      senderLabel = record.senderLabel,
      unread = record.unread,
      important = record.important,
      createdAt = record.createdAt.value,
      sourceBattleId = record.sourceBattleId.filter(_.trim.nonEmpty),
      sourcePath = record.sourcePath.filter(_.trim.nonEmpty),
      sourceLabel = record.sourceLabel.filter(_.trim.nonEmpty),
      friendRequestId = record.friendRequestMetadata.map(_.requestId.value),
      friendRequestStatus = record.friendRequestMetadata.map(_.status),
      friendRequestSourceHandle = record.friendRequestMetadata.map(_.sourceHandle.value),
      governanceActorHandle = record.governanceMetadata.map(_.actorHandle.value),
      governanceTargetPath = record.governanceMetadata.map(_.targetPath.value),
      governanceTargetLabel = record.governanceMetadata.map(_.targetLabel.value)
    )

  private def optionalString(value: Option[String]): Option[String] =
    value.filter(_.trim.nonEmpty)
}

final case class MailListResponse(mails: Vector[MailItemResponse])

object MailListResponse {
  given Encoder[MailListResponse] =
    Encoder.forProduct1("mails")(_.mails)

  def fromRecords(records: Vector[MailRecord]): MailListResponse =
    MailListResponse(records.map(MailItemResponse.fromRecord))
}

enum MailReadApiOutcome {
  case Read
}

object MailReadApiOutcome {
  def okFlag(outcome: MailReadApiOutcome): Boolean =
    outcome match {
      case MailReadApiOutcome.Read => true
    }
}

final case class MailReadResponse(outcome: MailReadApiOutcome)

object MailReadResponse {
  val Read: MailReadResponse =
    MailReadResponse(MailReadApiOutcome.Read)

  given Encoder[MailReadResponse] =
    Encoder.forProduct1("ok")(response => MailReadApiOutcome.okFlag(response.outcome))
}
