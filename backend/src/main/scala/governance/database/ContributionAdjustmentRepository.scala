package slaydemo.backend.governance.database

import slaydemo.backend.governance.objects.ContributionAdjustmentRecord

trait ContributionAdjustmentRepository {
  def list(limit: Int): Seq[ContributionAdjustmentRecord]
  def save(record: ContributionAdjustmentRecord): ContributionAdjustmentRecord
}
