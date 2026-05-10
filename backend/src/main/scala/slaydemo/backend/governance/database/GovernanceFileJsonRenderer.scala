package slaydemo.backend.governance.database

import slaydemo.backend.governance.objects.{
  ContributionAdjustmentRecord,
  GovernanceReviewKind,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType
}

private[database] object GovernanceFileJsonRenderer {
  def renderAdjustmentsPayload(records: Vector[ContributionAdjustmentRecord]): String = {
    val rendered = records.map(renderAdjustment).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.governance.contribution-adjustments.v1",
       |  "adjustments": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  def renderNotificationsPayload(records: Vector[GovernanceReviewNotificationRecord]): String = {
    val rendered = records.map(renderReviewNotification).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.governance.review-notifications.v1",
       |  "notifications": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderAdjustment(record: ContributionAdjustmentRecord): String =
    s"""    {
       |      "id": "${escape(record.id.value)}",
       |      "actorHandle": "${escape(record.actorHandle.value)}",
       |      "targetHandle": "${escape(record.targetHandle.value)}",
       |      "delta": ${record.delta.value},
       |      "reason": "${escape(record.reason.value)}",
       |      "createdAt": ${record.createdAt.value},
       |      "sourceLabel": "${escape(record.sourceLabel.value)}",
       |      "sourcePath": "${escape(record.sourcePath.value)}"
       |    }""".stripMargin

  private def renderReviewNotification(record: GovernanceReviewNotificationRecord): String =
    s"""    {
       |      "id": "${escape(record.id.value)}",
       |      "actorHandle": "${escape(record.actorHandle.value)}",
       |      "kind": "${escape(GovernanceReviewKind.wireValue(record.kind))}",
       |      "targetType": "${escape(GovernanceReviewTargetType.wireValue(record.targetType))}",
       |      "targetId": "${escape(record.targetId.value)}",
       |      "targetTitle": "${escape(record.targetTitle.value)}",
       |      "targetPath": "${escape(record.targetPath.value)}",
       |      "body": "${escape(record.body.value)}",
       |      "createdAt": ${record.createdAt.value},
       |      "mailId": "${escape(record.mailId.value)}"
       |    }""".stripMargin

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
