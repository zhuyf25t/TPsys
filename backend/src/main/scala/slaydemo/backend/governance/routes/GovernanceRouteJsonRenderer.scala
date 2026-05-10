package slaydemo.backend.governance.routes

import slaydemo.backend.governance.objects.*
import slaydemo.backend.governance.services.{ContributionAdjustmentSubmissionResult, GovernanceReviewNotificationSubmissionResult}
import slaydemo.backend.mail.objects.MailKind
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object GovernanceRouteJsonRenderer {
  def renderAdjustmentResult(result: ContributionAdjustmentSubmissionResult): String =
    renderObject(Vector("ok" -> "true", "adjustment" -> renderAdjustment(result.adjustment), "mail" -> renderMail(result.mail)))

  def renderNotificationResult(result: GovernanceReviewNotificationSubmissionResult): String =
    renderObject(Vector("ok" -> "true", "notification" -> renderNotification(result.notification), "mail" -> renderMail(result.mail)))

  def renderAdjustments(records: Vector[ContributionAdjustmentRecord]): String =
    renderObject(Vector("adjustments" -> records.map(renderAdjustment).mkString("[", ",", "]")))

  def renderNotifications(records: Vector[GovernanceReviewNotificationRecord]): String =
    renderObject(Vector("notifications" -> records.map(renderNotification).mkString("[", ",", "]")))

  private def renderAdjustment(record: ContributionAdjustmentRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(record.id.value),
        "actorHandle" -> jsonString(record.actorHandle.value),
        "targetHandle" -> jsonString(record.targetHandle.value),
        "delta" -> record.delta.value.toString,
        "reason" -> jsonString(record.reason.value),
        "createdAt" -> record.createdAt.value.toString,
        "sourceLabel" -> jsonString(record.sourceLabel.value),
        "sourcePath" -> jsonString(record.sourcePath.value)
      )
    )

  private def renderNotification(record: GovernanceReviewNotificationRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(record.id.value),
        "actorHandle" -> jsonString(record.actorHandle.value),
        "kind" -> jsonString(GovernanceReviewKind.wireValue(record.kind)),
        "targetType" -> jsonString(GovernanceReviewTargetType.wireValue(record.targetType)),
        "targetId" -> jsonString(record.targetId.value),
        "targetTitle" -> jsonString(record.targetTitle.value),
        "targetPath" -> jsonString(record.targetPath.value),
        "body" -> jsonString(record.body.value),
        "createdAt" -> record.createdAt.value.toString,
        "mailId" -> jsonString(record.mailId.value)
      )
    )

  private def renderMail(mail: GovernanceMailSnapshot): String =
    renderObject(
      Vector(
        "id" -> jsonString(mail.id.value),
        "ownerHandle" -> jsonString(mail.ownerHandle.value),
        "kind" -> jsonString(MailKind.wireValue(mail.kind)),
        "subject" -> jsonString(mail.subject),
        "excerpt" -> jsonString(mail.excerpt),
        "senderLabel" -> jsonString(mail.senderLabel),
        "unread" -> mail.unread.toString,
        "important" -> mail.important.toString,
        "createdAt" -> mail.createdAt.value.toString
      ) ++ governanceMetadataFields(mail)
    )

  private def governanceMetadataFields(mail: GovernanceMailSnapshot): Vector[(String, String)] =
    mail.governanceMetadata.map { metadata =>
      Vector(
        "governanceActorHandle" -> jsonString(metadata.actorHandle.value),
        "governanceTargetPath" -> jsonString(metadata.targetPath.value),
        "governanceTargetLabel" -> jsonString(metadata.targetLabel.value)
      )
    }.getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
