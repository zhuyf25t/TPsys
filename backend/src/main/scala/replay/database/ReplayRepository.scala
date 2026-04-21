package slaydemo.backend.replay.database

import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.shared.objects.ReplayId

trait ReplayRepository {
  def save(record: ReplayRecord): ReplayRecord
  def list(limit: Int): Seq[ReplayRecord]
  def findById(replayId: ReplayId): Option[ReplayRecord]
}
