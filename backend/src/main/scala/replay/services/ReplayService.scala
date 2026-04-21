package slaydemo.backend.replay.services

import slaydemo.backend.replay.api.{ReplayCatalogView, ReplayDetailView, ReplaySubmissionRequest}
import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.shared.objects.ReplayId

trait ReplayService {
  def record(request: ReplaySubmissionRequest): Either[String, ReplayRecord]
  def list(limit: Int): Seq[ReplayCatalogView]
  def load(replayId: ReplayId): Option[ReplayDetailView]
}
