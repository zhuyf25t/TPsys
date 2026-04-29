package slaydemo.backend.replay.database

import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayRecord}
import slaydemo.backend.shared.objects.ReplayId

trait ReplayRepository {
  def save(record: ReplayRecord): ReplayRecord
  def list(limit: Int): Seq[ReplayRecord]
  def findById(replayId: ReplayId): Option[ReplayRecord]
  def delete(replayId: ReplayId): Unit
  def saveComment(record: ReplayCommentRecord): ReplayCommentRecord
  def listComments(replayId: ReplayId, limit: Int): Seq[ReplayCommentRecord]
}
