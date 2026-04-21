package slaydemo.backend.governance.api

import slaydemo.backend.shared.objects.UserId

final case class ContributionView(
  userId: UserId,
  handle: String,
  contributionScore: Int
)

trait GovernanceApi {
  def listContributionBoard(): Vector[ContributionView]
}
