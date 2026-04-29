package slaydemo.backend.forum.objects

import slaydemo.backend.shared.objects.ThreadId

final case class ForumThread(
  threadId: ThreadId,
  title: String,
  body: String,
  authorHandle: String
)
