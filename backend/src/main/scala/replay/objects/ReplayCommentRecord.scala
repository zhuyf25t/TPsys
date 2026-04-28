package slaydemo.backend.replay.objects

import slaydemo.backend.shared.objects.{ReplayId, UserId}

final case class ReplayCommentRecord(
  id: String,
  replayId: ReplayId,
  authorHandle: UserId,
  body: String,
  createdAt: Long
)
