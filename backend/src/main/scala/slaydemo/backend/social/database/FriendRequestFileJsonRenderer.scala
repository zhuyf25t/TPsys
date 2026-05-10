package slaydemo.backend.social.database

import slaydemo.backend.social.objects.{FriendRequestRecord, FriendRequestStatus}

private[database] object FriendRequestFileJsonRenderer {
  def renderPayload(records: Vector[FriendRequestRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.friend-requests.v1",
       |  "requests": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: FriendRequestRecord): String =
    s"""    {
       |      "id": "${escape(record.id.value)}",
       |      "sourceHandle": "${escape(record.sourceHandle.value)}",
       |      "targetHandle": "${escape(record.targetHandle.value)}",
       |      "createdAt": ${record.createdAt.value},
       |      "status": "${escape(FriendRequestStatus.wireValue(record.status))}",
       |      "respondedAt": ${record.respondedAt.map(_.value.toString).getOrElse("null")}
       |    }""".stripMargin

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
