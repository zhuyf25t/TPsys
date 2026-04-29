package slaydemo.backend.governance.planners

import slaydemo.backend.governance.api.ContributionView

trait GovernancePlanner {
  def buildContributionBoard(): Vector[ContributionView]
}
