package slaydemo.backend.governance.policies

trait GovernancePolicy {
  def canAdjustContribution(isAdmin: Boolean): Boolean
}
