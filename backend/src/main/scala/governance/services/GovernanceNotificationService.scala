package slaydemo.backend.governance.services

import java.util.UUID

import slaydemo.backend.governance.database.GovernanceReviewNotificationRepository
import slaydemo.backend.governance.objects.GovernanceReviewNotificationRecord
import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.mails.services.MailService

final case class GovernanceReviewNotificationSubmissionResult(
  record: GovernanceReviewNotificationRecord,
  mail: MailRecord
)

trait GovernanceNotificationService {
  def listReviewNotifications(
    kind: Option[String],
    targetType: Option[String],
    limit: Int
  ): Seq[GovernanceReviewNotificationRecord]

  def findReviewNotificationByMailId(mailId: String): Option[GovernanceReviewNotificationRecord]

  def createReviewNotification(
    actorHandle: String,
    kind: String,
    targetType: String,
    targetId: String,
    targetTitle: String,
    targetPath: String,
    body: String
  ): Either[String, GovernanceReviewNotificationSubmissionResult]
}

final class DefaultGovernanceNotificationService(
  repository: GovernanceReviewNotificationRepository,
  mailService: MailService
) extends GovernanceNotificationService {
  override def listReviewNotifications(
    kind: Option[String],
    targetType: Option[String],
    limit: Int
  ): Seq[GovernanceReviewNotificationRecord] =
    repository.list(kind, targetType, limit)

  override def findReviewNotificationByMailId(mailId: String): Option[GovernanceReviewNotificationRecord] =
    repository.findByMailId(mailId)

  override def createReviewNotification(
    actorHandle: String,
    kind: String,
    targetType: String,
    targetId: String,
    targetTitle: String,
    targetPath: String,
    body: String
  ): Either[String, GovernanceReviewNotificationSubmissionResult] = {
    val normalizedActor = actorHandle.trim.take(32) match {
      case ""    => "Visitor"
      case value => value
    }
    val normalizedKind = kind.trim
    val normalizedTargetType = targetType.trim
    val normalizedTargetId = targetId.trim.take(160)
    val normalizedTargetTitle = targetTitle.trim.take(160)
    val normalizedTargetPath = targetPath.trim.take(240)
    val normalizedBody = body.trim.take(500)

    kindLabel(normalizedKind) match {
      case None =>
        Left("invalid_kind")
      case Some(label) if !isSupportedTargetType(normalizedTargetType) || normalizedTargetId.isEmpty =>
        Left("invalid_target")
      case Some(label) if normalizedBody.isEmpty =>
        Left("invalid_body")
      case Some(label) =>
        val now = System.currentTimeMillis()
        val mailId = s"mail-governance-review-${UUID.randomUUID().toString.replace("-", "").take(12)}"
        val targetLabel = if (normalizedTargetTitle.nonEmpty) normalizedTargetTitle else normalizedTargetId
        val mail = MailRecord(
          id = mailId,
          ownerHandle = "admin",
          kind = "governance",
          subject = s"[待处理] $label：${targetLabel.take(36)}",
          excerpt = buildExcerpt(
            normalizedActor,
            label,
            normalizedTargetType,
            normalizedTargetId,
            targetLabel,
            normalizedTargetPath,
            normalizedBody
          ),
          senderLabel = s"治理通知 @${normalizedActor}",
          unread = true,
          important = true,
          createdAt = now
        )
        val createdMail = mailService.create(mail)
        val record = GovernanceReviewNotificationRecord(
          id = s"governance-review-${UUID.randomUUID().toString.replace("-", "").take(12)}",
          actorHandle = normalizedActor,
          kind = normalizedKind,
          targetType = normalizedTargetType,
          targetId = normalizedTargetId,
          targetTitle = normalizedTargetTitle,
          targetPath = normalizedTargetPath,
          body = normalizedBody,
          createdAt = now,
          mailId = createdMail.id
        )

        Right(GovernanceReviewNotificationSubmissionResult(repository.save(record), createdMail))
    }
  }

  private def buildExcerpt(
    actorHandle: String,
    kindLabel: String,
    targetType: String,
    targetId: String,
    targetLabel: String,
    targetPath: String,
    body: String
  ): String = {
    val path = if (targetPath.trim.nonEmpty) s" 链接：${targetPath.trim}" else ""
    s"@$actorHandle 提交了$kindLabel。目标：$targetLabel ($targetType:$targetId)。$path 说明：$body"
  }

  private def kindLabel(kind: String): Option[String] =
    kind match {
      case "replay_proposal"  => Some("回放提议")
      case "replay_report"    => Some("回放举报")
      case "discussion_report" => Some("论坛举报")
      case "bot_suggestion"   => Some("机器人建议")
      case _                  => None
    }

  private def isSupportedTargetType(targetType: String): Boolean =
    targetType == "replay" || targetType == "discussion" || targetType == "bot"
}
