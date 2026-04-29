package slaydemo.backend.forum.policies

trait ForumPolicy {
  def canCreateThread(reputationScore: Int): Boolean
}
