package services.replay.api

import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.objects.ReplayId
import services.replay.services.{ReplayCommentCommand, ReplayRecordCommand}

object ReplayCommandParsers {
  def parseRecordCommand(
    message: ReplayRecordAPIMessage
  ): Either[ReplayRecordDecodeError, ReplayRecordCommand] =
    for
      replayId <- message.replayId.toRight(ReplayRecordDecodeError.InvalidReplayId)
      battleId <- message.battleId.toRight(ReplayRecordDecodeError.InvalidBattleId)
      handle <- parseRecordHandle(message.handle)
    yield ReplayRecordCommand(
      replayId = replayId,
      battleId = battleId,
      handle = handle,
      displayName = message.displayName.getOrElse(DisplayName(handle.value)),
      finishedAt = message.finishedAt,
      finishedAtLabel = message.finishedAtLabel.value,
      title = message.title.value,
      modeLabel = message.modeLabel.value,
      resultLabel = message.resultLabel.value,
      mapLabel = message.mapLabel.value,
      highlightLine = message.highlightLine.value,
      coverLabel = message.coverLabel.value,
      playersLine = message.playersLine.value,
      timelineHint = message.timelineHint.value,
      score = message.score,
      placement = message.placement,
      durationMs = message.durationMs,
      survivalOutcome = message.survivalOutcome,
      thumbnailDataUrl = message.thumbnailDataUrl.value,
      currentLoadout = message.currentLoadout.value,
      frameCount = message.frameCount,
      requestedPlaybackAvailability = message.playbackAvailability,
      framesJson = ReplayRecordFramesInput.fromWire(message.framesJson.map(_.value), message.frames).value
    )

  def parseCommentCommand(
    message: ReplayCommentCreateAPIMessage
  ): Either[ReplayCommentDecodeError, ReplayCommentCommand] =
    for
      replayId <- message.replayId.toRight(ReplayCommentDecodeError.InvalidReplayId)
      author <- parseCommentAuthor(message.authorHandle.getOrElse(ReplayCommentAuthorInput.Invalid))
    yield ReplayCommentCommand(
      replayId = replayId,
      authorHandle = author,
      body = message.body.getOrElse(ReplayCommentBodyInput.fromWire(None)).value
    )

  private def parseRecordHandle(value: ReplayRecordHandleInput): Either[ReplayRecordDecodeError, PlayerHandle] =
    value match {
      case ReplayRecordHandleInput.Valid(handle)      => Right(handle)
      case ReplayRecordHandleInput.Invalid            => Left(ReplayRecordDecodeError.InvalidHandle)
      case ReplayRecordHandleInput.VisitorNotAllowed  => Left(ReplayRecordDecodeError.VisitorNotAllowed)
    }

  private def parseCommentAuthor(
    value: ReplayCommentAuthorInput
  ): Either[ReplayCommentDecodeError, PlayerHandle] =
    value match {
      case ReplayCommentAuthorInput.Valid(handle)      => Right(handle)
      case ReplayCommentAuthorInput.Invalid            => Left(ReplayCommentDecodeError.InvalidAuthorHandle)
      case ReplayCommentAuthorInput.VisitorNotAllowed  => Left(ReplayCommentDecodeError.VisitorNotAllowed)
    }
}
