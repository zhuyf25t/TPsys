package slaydemo.backend.forum.database

import slaydemo.backend.forum.objects.ForumThread
import slaydemo.backend.shared.objects.ThreadId

trait ForumRepository {
  def findThread(threadId: ThreadId): Option[ForumThread]
}
