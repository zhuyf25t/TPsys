package slaydemo.backend.forum.planners

import slaydemo.backend.forum.api.ForumThreadSummary

trait ForumPlanner {
  def buildThreadSummaries(): Vector[ForumThreadSummary]
}
