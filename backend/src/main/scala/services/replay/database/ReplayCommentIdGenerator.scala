package services.replay.database

import java.util.UUID

import services.replay.objects.ReplayCommentId

private[database] trait ReplayCommentIdGenerator {
  def nextCommentId(): ReplayCommentId
}

private[database] object RandomReplayCommentIdGenerator extends ReplayCommentIdGenerator {
  override def nextCommentId(): ReplayCommentId =
    ReplayCommentId(s"comment-${UUID.randomUUID().toString}")
}
