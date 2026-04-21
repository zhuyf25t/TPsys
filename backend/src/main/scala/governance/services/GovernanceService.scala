package slaydemo.backend.governance.services

import slaydemo.backend.governance.objects.GovernanceRecord
import slaydemo.backend.shared.objects.UserId

trait GovernanceService {
  def loadGovernanceRecord(userId: UserId): Option[GovernanceRecord]
}
