package slaydemo.backend.replay.database

import slaydemo.backend.replay.objects.{ReplayCommentId, ReplayCommentRecord, ReplayId, ReplayRecord}

trait ReplayRepository {
  def saveReplay(record: ReplayRecord): ReplayRecord
  def listReplays(limit: Int): Vector[ReplayRecord]
  def findReplayById(replayId: ReplayId): Option[ReplayRecord]
  def nextCommentId(): ReplayCommentId
  def saveComment(record: ReplayCommentRecord): ReplayCommentRecord

  /** Returns the latest comments up to `limit`, ordered chronologically for display. */
  def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord]
}
