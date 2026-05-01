package slaydemo.backend.replay.database

import slaydemo.backend.replay.objects.{ReplayCommentId, ReplayCommentRecord, ReplayId, ReplayRecord}

final class InMemoryReplayRepository extends ReplayRepository {
  private val lock = Object()
  private var replays: Map[ReplayId, ReplayRecord] = Map.empty
  private var commentsByReplayId: Map[ReplayId, Vector[ReplayCommentRecord]] = Map.empty
  private var nextCommentNumber: Long = 1L

  override def saveReplay(record: ReplayRecord): ReplayRecord = {
    lock.synchronized {
      replays = replays.updated(record.replayId, record)
    }
    record
  }

  override def listReplays(limit: Int): Vector[ReplayRecord] =
    lock.synchronized {
      replays.values.toVector
    }.sortWith(compareRecentFirst).take(math.max(0, limit))

  override def findReplayById(replayId: ReplayId): Option[ReplayRecord] =
    lock.synchronized {
      replays.get(replayId)
    }

  override def nextCommentId(): ReplayCommentId =
    lock.synchronized {
      val id = ReplayCommentId(f"comment-$nextCommentNumber%06d")
      nextCommentNumber += 1L
      id
    }

  override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord = {
    lock.synchronized {
      commentsByReplayId = commentsByReplayId.updated(
        record.replayId,
        commentsByReplayId.getOrElse(record.replayId, Vector.empty) :+ record
      )
    }
    record
  }

  override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
    lock.synchronized {
      commentsByReplayId.getOrElse(replayId, Vector.empty)
    }.sortBy(_.createdAt.value).takeRight(math.max(0, limit))

  private def compareRecentFirst(left: ReplayRecord, right: ReplayRecord): Boolean =
    if left.finishedAt.value != right.finishedAt.value then left.finishedAt.value > right.finishedAt.value
    else left.replayId.value < right.replayId.value
}

object InMemoryReplayRepository {
  def apply(): InMemoryReplayRepository =
    new InMemoryReplayRepository()
}
