package slaydemo.backend.replay.services

import slaydemo.backend.replay.objects.{ReplayFrameCount, ReplayPlaybackAvailability, ReplayRecord, ReplayTitle}
import slaydemo.backend.replay.support.ReplayFrameJson

private[services] object ReplayRecordFactory {
  def fromCommand(
    command: ReplayRecordCommand,
    replayFrames: ReplayFrameJson.Normalized
  ): ReplayRecord = {
    val playbackAvailability = ReplayPlaybackAvailability.resolve(
      requested = command.requestedPlaybackAvailability,
      frames = ReplayPlaybackAvailability.fromAvailableFlag(replayFrames.playbackAvailable)
    )

    ReplayRecord(
      replayId = command.replayId,
      battleId = command.battleId,
      handle = command.handle,
      displayName = command.displayName,
      finishedAt = command.finishedAt,
      finishedAtLabel = command.finishedAtLabel,
      title = ReplayTitle.fromWire(command.title),
      modeLabel = command.modeLabel,
      resultLabel = command.resultLabel,
      mapLabel = command.mapLabel,
      highlightLine = command.highlightLine,
      coverLabel = command.coverLabel,
      playersLine = command.playersLine,
      timelineHint = command.timelineHint,
      score = command.score,
      placement = command.placement,
      ratingBefore = None,
      ratingDelta = None,
      ratingAfter = None,
      durationMs = command.durationMs,
      survivalOutcome = command.survivalOutcome,
      thumbnailDataUrl = command.thumbnailDataUrl.flatMap(nonEmpty),
      currentLoadout = command.currentLoadout.flatMap(nonEmpty),
      frameCount = ReplayFrameCount.fromWire(replayFrames.frameCount),
      playbackAvailability = playbackAvailability,
      framesJson = replayFrames.framesJson
    )
  }

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
