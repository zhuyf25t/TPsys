package slaydemo.backend.mail.database

import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}

private[database] object MailFileJsonRenderer {
  def renderPayload(records: Vector[MailRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.mails.v1",
       |  "mails": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: MailRecord): String =
    s"""    {
       |      "id": "${escape(record.id.value)}",
       |      "ownerHandle": "${escape(record.ownerHandle.value)}",
       |      "kind": "${escape(MailKind.wireValue(record.kind))}",
       |      "subject": "${escape(record.subject)}",
       |      "excerpt": "${escape(record.excerpt)}",
       |      "senderLabel": "${escape(record.senderLabel)}",
       |      "unread": ${record.unread},
       |      "important": ${record.important},
       |      "createdAt": ${record.createdAt.value},
       |      "sourceBattleId": ${renderNullableString(record.sourceBattleId)},
       |      "sourcePath": ${renderNullableString(record.sourcePath)},
       |      "sourceLabel": ${renderNullableString(record.sourceLabel)},
       |      "governanceActorHandle": ${renderNullableString(record.governanceMetadata.map(_.actorHandle.value))},
       |      "governanceTargetPath": ${renderNullableString(record.governanceMetadata.map(_.targetPath.value))},
       |      "governanceTargetLabel": ${renderNullableString(record.governanceMetadata.map(_.targetLabel.value))},
       |      "friendRequestId": ${renderNullableString(record.friendRequestMetadata.map(_.requestId.value))},
       |      "friendRequestStatus": ${renderNullableString(record.friendRequestMetadata.map(metadata => MailFriendRequestStatus.wireValue(metadata.status)))},
       |      "friendRequestSourceHandle": ${renderNullableString(record.friendRequestMetadata.map(_.sourceHandle.value))}
       |    }""".stripMargin

  private def renderNullableString(value: Option[String]): String =
    value.map(text => s""""${escape(text)}"""").getOrElse("null")

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
