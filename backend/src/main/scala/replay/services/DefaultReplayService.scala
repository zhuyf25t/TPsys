package slaydemo.backend.replay.services

import java.nio.charset.StandardCharsets
import java.util.Base64

import slaydemo.backend.replay.api.{ReplayCatalogView, ReplayDetailView, ReplaySubmissionRequest}
import slaydemo.backend.replay.database.ReplayRepository
import slaydemo.backend.replay.objects.ReplayRecord
import slaydemo.backend.shared.objects.ReplayId

final class DefaultReplayService(repository: ReplayRepository) extends ReplayService {
  override def record(request: ReplaySubmissionRequest): Either[String, ReplayRecord] = {
    val replayId = request.replayId.value.trim
    val battleId = request.battleId.value.trim
    val handle = request.handle.value.trim

    if (replayId.isEmpty) {
      Left("invalid_replay_id")
    } else if (battleId.isEmpty) {
      Left("invalid_battle_id")
    } else if (handle.isEmpty) {
      Left("invalid_handle")
    } else {
      val record = ReplayRecord(
        replayId = request.replayId,
        battleId = request.battleId,
        handle = request.handle,
        displayName = request.displayName.trim,
        finishedAt = request.finishedAt,
        finishedAtLabel = request.finishedAtLabel.trim,
        title = request.title.trim,
        modeLabel = request.modeLabel.trim,
        resultLabel = request.resultLabel.trim,
        mapLabel = request.mapLabel.trim,
        highlightLine = request.highlightLine.trim,
        coverLabel = request.coverLabel.trim,
        playersLine = request.playersLine.trim,
        timelineHint = request.timelineHint.trim,
        score = request.score,
        placement = request.placement,
        durationMs = request.durationMs,
        aliveAtEnd = request.aliveAtEnd,
        thumbnailDataUrl = request.thumbnailDataUrl.map(_.trim).filter(_.nonEmpty),
        currentLoadout = request.currentLoadout.map(_.trim).filter(_.nonEmpty),
        frameCount = request.frameCount.max(0),
        playbackAvailable = request.playbackAvailable,
        framesJsonB64 = Base64.getEncoder.encodeToString(request.framesJson.trim.getBytes(StandardCharsets.UTF_8))
      )

      Right(repository.save(record))
    }
  }

  override def list(limit: Int): Seq[ReplayCatalogView] = {
    val bounded = limit.max(1).min(50)
    repository.list(bounded).map(toCatalogView)
  }

  override def load(replayId: ReplayId): Option[ReplayDetailView] = {
    repository.findById(replayId).map(toDetailView)
  }

  private def toCatalogView(record: ReplayRecord): ReplayCatalogView = {
    ReplayCatalogView(
      replayId = record.replayId,
      battleId = record.battleId,
      title = record.title,
      modeLabel = record.modeLabel,
      resultLabel = record.resultLabel,
      finishedAtLabel = record.finishedAtLabel,
      mapLabel = record.mapLabel,
      highlightLine = record.highlightLine,
      coverLabel = record.coverLabel,
      playersLine = record.playersLine,
      timelineHint = record.timelineHint,
      score = record.score,
      placement = record.placement,
      durationMs = record.durationMs,
      aliveAtEnd = record.aliveAtEnd,
      thumbnailDataUrl = record.thumbnailDataUrl,
      frameCount = record.frameCount,
      playbackAvailable = record.playbackAvailable
    )
  }

  private def toDetailView(record: ReplayRecord): ReplayDetailView = {
    ReplayDetailView(
      replayId = record.replayId,
      battleId = record.battleId,
      handle = record.handle,
      displayName = record.displayName,
      finishedAt = record.finishedAt,
      finishedAtLabel = record.finishedAtLabel,
      title = record.title,
      modeLabel = record.modeLabel,
      resultLabel = record.resultLabel,
      mapLabel = record.mapLabel,
      highlightLine = record.highlightLine,
      coverLabel = record.coverLabel,
      playersLine = record.playersLine,
      timelineHint = record.timelineHint,
      score = record.score,
      placement = record.placement,
      durationMs = record.durationMs,
      aliveAtEnd = record.aliveAtEnd,
      thumbnailDataUrl = record.thumbnailDataUrl,
      currentLoadout = record.currentLoadout,
      frameCount = record.frameCount,
      playbackAvailable = record.playbackAvailable,
      framesJson = new String(Base64.getDecoder.decode(record.framesJsonB64), StandardCharsets.UTF_8)
    )
  }
}
