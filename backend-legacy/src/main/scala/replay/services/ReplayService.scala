package slaydemo.backend.replay.services

import slaydemo.backend.replay.api.{ReplayCatalogView, ReplayCommentSubmissionRequest, ReplayCommentView, ReplayDetailView, ReplaySubmissionRequest}
import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.shared.objects.ReplayId

trait ReplayService {
  def record(request: ReplaySubmissionRequest): Either[String, ReplayRecord]
  def list(limit: Int): Seq[ReplayCatalogView]
  def load(replayId: ReplayId): Option[ReplayDetailView]
  def listComments(replayId: ReplayId, limit: Int): Seq[ReplayCommentView]
  def addComment(request: ReplayCommentSubmissionRequest): Either[String, ReplayCommentView]
}
