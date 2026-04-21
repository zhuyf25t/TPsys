package slaydemo.backend.governance.database

import slaydemo.backend.governance.objects.GovernanceRecord
import slaydemo.backend.shared.objects.UserId

trait GovernanceRepository {
  def findRecord(userId: UserId): Option[GovernanceRecord]
}
