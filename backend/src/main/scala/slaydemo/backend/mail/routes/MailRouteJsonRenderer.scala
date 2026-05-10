package slaydemo.backend.mail.routes

import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object MailRouteJsonRenderer {
  def renderMails(records: Vector[MailRecord]): String =
    renderObject(Vector("mails" -> records.map(renderMail).mkString("[", ",", "]")))

  private def renderMail(record: MailRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(record.id.value),
        "ownerHandle" -> jsonString(record.ownerHandle.value),
        "kind" -> jsonString(MailKind.wireValue(record.kind)),
        "subject" -> jsonString(record.subject),
        "excerpt" -> jsonString(record.excerpt),
        "senderLabel" -> jsonString(record.senderLabel),
        "unread" -> record.unread.toString,
        "important" -> record.important.toString,
        "createdAt" -> record.createdAt.value.toString
      ) ++ optionalStringField("sourceBattleId", record.sourceBattleId) ++
        optionalStringField("sourcePath", record.sourcePath) ++
        optionalStringField("sourceLabel", record.sourceLabel) ++
        friendRequestMetadataFields(record) ++
        governanceMetadataFields(record)
    )

  private def friendRequestMetadataFields(record: MailRecord): Vector[(String, String)] =
    record.friendRequestMetadata.map { metadata =>
      Vector(
        "friendRequestId" -> jsonString(metadata.requestId.value),
        "friendRequestStatus" -> jsonString(MailFriendRequestStatus.wireValue(metadata.status)),
        "friendRequestSourceHandle" -> jsonString(metadata.sourceHandle.value)
      )
    }.getOrElse(Vector.empty)

  private def governanceMetadataFields(record: MailRecord): Vector[(String, String)] =
    record.governanceMetadata.map { metadata =>
      Vector(
        "governanceActorHandle" -> jsonString(metadata.actorHandle.value),
        "governanceTargetPath" -> jsonString(metadata.targetPath.value),
        "governanceTargetLabel" -> jsonString(metadata.targetLabel.value)
      )
    }.getOrElse(Vector.empty)

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
