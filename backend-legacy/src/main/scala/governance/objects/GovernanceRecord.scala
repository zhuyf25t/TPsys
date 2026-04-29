package slaydemo.backend.governance.objects

import slaydemo.backend.shared.objects.UserId

final case class GovernanceRecord(
  userId: UserId,
  contributionScore: Int,
  ratingScore: Int
)
