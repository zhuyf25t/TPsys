package slaydemo.backend.forum.ports

import slaydemo.backend.shared.objects.ThreadId

trait ModerationPort {
  def flagThread(threadId: ThreadId, reason: String): Unit
}
