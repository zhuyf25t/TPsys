package slaydemo.backend.forum.services

import slaydemo.backend.forum.objects.ForumThread
import slaydemo.backend.shared.objects.ThreadId

trait ForumService {
  def loadThread(threadId: ThreadId): Option[ForumThread]
}
