package slaydemo.backend.governance.services

import java.util.UUID

import slaydemo.backend.governance.database.ContributionAdjustmentRepository
import slaydemo.backend.governance.objects.ContributionAdjustmentRecord
import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.mails.services.MailService

final class DefaultContributionAdjustmentService(
  repository: ContributionAdjustmentRepository,
  mailService: MailService
) extends ContributionAdjustmentService {
  override def list(limit: Int): Seq[ContributionAdjustmentRecord] =
    repository.list(math.max(0, math.min(limit, 1000)))

  override def create(
    actorHandle: String,
    targetHandle: String,
    delta: Int,
    reason: String,
    sourceLabel: String,
    sourcePath: String
  ): Either[String, ContributionAdjustmentSubmissionResult] = {
    val normalizedActor = actorHandle.trim
    val normalizedTarget = targetHandle.trim
    val normalizedReason = reason.trim.take(240)
    val normalizedSourceLabel = sourceLabel.trim.take(120)
    val normalizedSourcePath = sourcePath.trim.take(240)

    if (!isBuiltinAdminHandle(normalizedActor)) {
      Left("invalid_actor")
    } else if (normalizedTarget.isEmpty) {
      Left("invalid_target")
    } else if (delta == 0) {
      Left("invalid_delta")
    } else {
      val createdAt = System.currentTimeMillis()
      val record = ContributionAdjustmentRecord(
        id = s"governance-${UUID.randomUUID().toString.replace("-", "").take(12)}",
        actorHandle = normalizedActor,
        targetHandle = normalizedTarget,
        delta = delta,
        reason = normalizedReason,
        createdAt = createdAt,
        sourceLabel = normalizedSourceLabel,
        sourcePath = normalizedSourcePath
      )

      repository.save(record)
      val mail = mailService.create(buildMail(record))
      Right(ContributionAdjustmentSubmissionResult(record, mail))
    }
  }

  private def buildMail(record: ContributionAdjustmentRecord): MailRecord = {
    MailRecord(
      id = s"mail-${record.id}",
      ownerHandle = record.targetHandle,
      kind = "governance",
      subject = s"贡献裁决 ${formatDelta(record.delta)}",
      excerpt = buildMailExcerpt(record),
      senderLabel = s"管理员 @${record.actorHandle}",
      unread = true,
      important = true,
      createdAt = record.createdAt
    )
  }

  private def buildMailExcerpt(record: ContributionAdjustmentRecord): String = {
    val base = s"@${record.actorHandle} 对你的贡献值进行了 ${formatDelta(record.delta)} 调整。"
    val reason = if (record.reason.isEmpty) "" else s" 原因：${record.reason}"
    val source = formatSource(record)
    s"$base$reason$source"
  }

  private def formatSource(record: ContributionAdjustmentRecord): String = {
    val label = record.sourceLabel.trim
    val path = record.sourcePath.trim
    if (label.isEmpty && path.isEmpty) {
      ""
    } else if (label.nonEmpty && path.nonEmpty) {
      s" 来源：$label $path"
    } else {
      s" 来源：${if (label.nonEmpty) label else path}"
    }
  }

  private def formatDelta(delta: Int): String =
    if (delta > 0) s"+$delta" else delta.toString

  private def isBuiltinAdminHandle(handle: String): Boolean =
    handle.trim.equalsIgnoreCase("admin")
}
