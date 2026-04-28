package slaydemo.backend.governance.services

import slaydemo.backend.governance.objects.ContributionAdjustmentRecord
import slaydemo.backend.mails.objects.MailRecord

final case class ContributionAdjustmentSubmissionResult(
  record: ContributionAdjustmentRecord,
  mail: MailRecord
)

trait ContributionAdjustmentService {
  def list(limit: Int): Seq[ContributionAdjustmentRecord]
  def create(
    actorHandle: String,
    targetHandle: String,
    delta: Int,
    reason: String,
    sourceLabel: String,
    sourcePath: String
  ): Either[String, ContributionAdjustmentSubmissionResult]
}
