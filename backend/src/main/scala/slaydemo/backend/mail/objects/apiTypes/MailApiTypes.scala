package slaydemo.backend.mail.objects.apiTypes

import io.circe.syntax.*
import io.circe.{Encoder, Json}

import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}

final case class MailItemResponse(
  id: String,
  ownerHandle: String,
  kind: String,
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
  friendRequestStatus: Option[String],
  friendRequestSourceHandle: Option[String],
  governanceActorHandle: Option[String],
  governanceTargetPath: Option[String],
  governanceTargetLabel: Option[String]
)

object MailItemResponse {
  given Encoder[MailItemResponse] =
    Encoder.instance { item =>
      Json.obj(
        (
          Vector(
            "id" -> Json.fromString(item.id),
            "ownerHandle" -> Json.fromString(item.ownerHandle),
            "kind" -> Json.fromString(item.kind),
            "subject" -> Json.fromString(item.subject),
            "excerpt" -> Json.fromString(item.excerpt),
            "senderLabel" -> Json.fromString(item.senderLabel),
            "unread" -> Json.fromBoolean(item.unread),
            "important" -> Json.fromBoolean(item.important),
            "createdAt" -> Json.fromLong(item.createdAt)
          ) ++ optionalStringField("sourceBattleId", item.sourceBattleId) ++
            optionalStringField("sourcePath", item.sourcePath) ++
            optionalStringField("sourceLabel", item.sourceLabel) ++
            optionalStringField("friendRequestId", item.friendRequestId) ++
            optionalStringField("friendRequestStatus", item.friendRequestStatus) ++
            optionalStringField("friendRequestSourceHandle", item.friendRequestSourceHandle) ++
            optionalStringField("governanceActorHandle", item.governanceActorHandle) ++
            optionalStringField("governanceTargetPath", item.governanceTargetPath) ++
            optionalStringField("governanceTargetLabel", item.governanceTargetLabel)
        )*
      )
    }

  def fromRecord(record: MailRecord): MailItemResponse =
    MailItemResponse(
      id = record.id.value,
      ownerHandle = record.ownerHandle.value,
      kind = MailKind.wireValue(record.kind),
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
      friendRequestStatus = record.friendRequestMetadata.map(metadata => MailFriendRequestStatus.wireValue(metadata.status)),
      friendRequestSourceHandle = record.friendRequestMetadata.map(_.sourceHandle.value),
      governanceActorHandle = record.governanceMetadata.map(_.actorHandle.value),
      governanceTargetPath = record.governanceMetadata.map(_.targetPath.value),
      governanceTargetLabel = record.governanceMetadata.map(_.targetLabel.value)
    )

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, Json)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> Json.fromString(text))).getOrElse(Vector.empty)
}

final case class MailListResponse(mails: Vector[MailItemResponse])

object MailListResponse {
  given Encoder[MailListResponse] =
    Encoder.forProduct1("mails")(_.mails)

  def fromRecords(records: Vector[MailRecord]): MailListResponse =
    MailListResponse(records.map(MailItemResponse.fromRecord))

  def renderRecords(records: Vector[MailRecord]): String =
    fromRecords(records).asJson.noSpaces
}

final case class MailReadResponse(ok: Boolean)

object MailReadResponse {
  given Encoder[MailReadResponse] =
    Encoder.forProduct1("ok")(_.ok)

  val Ok: MailReadResponse =
    MailReadResponse(ok = true)

  def render(response: MailReadResponse): String =
    response.asJson.noSpaces

  def renderOk: String =
    render(Ok)
}
