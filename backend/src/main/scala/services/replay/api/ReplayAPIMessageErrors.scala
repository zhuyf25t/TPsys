package services.replay.api

import cats.effect.IO
import services.replay.objects.{ReplayCommentRecord, ReplayRecord}
import services.replay.services.{ReplayCommentError, ReplayRecordError}
import system.api.APIMessageError

private[api] object ReplayAPIMessageErrors {
  def recordDecode(error: ReplayRecordDecodeError): APIMessageError =
    ReplayAPIMessageSupport.recordDecodeError(error)

  def recordService(result: Either[ReplayRecordError, ReplayRecord]): IO[ReplayRecord] =
    result.fold(
      error => IO.raiseError(ReplayAPIMessageSupport.recordServiceError(error)),
      IO.pure
    )

  def commentDecode(error: ReplayCommentDecodeError): APIMessageError =
    ReplayAPIMessageSupport.commentDecodeError(error)

  def commentService(result: Either[ReplayCommentError, ReplayCommentRecord]): IO[ReplayCommentRecord] =
    result.fold(
      error => IO.raiseError(ReplayAPIMessageSupport.commentServiceError(error)),
      IO.pure
    )

  def replayLoad(result: Option[ReplayRecord]): IO[ReplayRecord] =
    result.fold(
      IO.raiseError[ReplayRecord](ReplayAPIMessageSupport.error(ReplayApiErrorCode.ReplayNotFound))
    )(IO.pure)
}
