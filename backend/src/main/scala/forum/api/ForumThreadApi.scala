package slaydemo.backend.forum.api

import slaydemo.backend.shared.objects.ThreadId

final case class ForumThreadSummary(
  threadId: ThreadId,
  title: String,
  authorHandle: String
)

trait ForumThreadApi {
  def listThreads(): Vector[ForumThreadSummary]
}
