package slaydemo.backend.identity.policies

trait IdentityPolicy {
  def canIssueSession(active: Boolean): Boolean
}
