package services.replay.api

import io.circe.Error

import system.api.APIMessageError

object ReplayAPIMessageSupport {
  def invalidJsonObject(error: Error): APIMessageError =
    ReplayAPIMessageSupport.error(ReplayApiErrorCode.BadJsonObject)

  def recordDecodeError(error: ReplayRecordDecodeError): APIMessageError =
    ReplayAPIMessageSupport.error(ReplayApiErrorMapper.recordDecodeErrorCode(error))

  def recordServiceError(error: services.replay.services.ReplayRecordError): APIMessageError =
    ReplayAPIMessageSupport.error(ReplayApiErrorMapper.recordServiceErrorCode(error))

  def commentDecodeError(error: ReplayCommentDecodeError): APIMessageError =
    ReplayAPIMessageSupport.error(ReplayApiErrorMapper.commentDecodeErrorCode(error))

  def commentServiceError(error: services.replay.services.ReplayCommentError): APIMessageError =
    ReplayAPIMessageSupport.error(ReplayApiErrorMapper.commentServiceErrorCode(error))

  def error(code: ReplayApiErrorCode): APIMessageError =
    code match {
      case ReplayApiErrorCode.VisitorNotAllowed =>
        APIMessageError.Forbidden(ReplayApiErrorCode.message(code))
      case ReplayApiErrorCode.ReplayNotFound =>
        APIMessageError.NotFound(ReplayApiErrorCode.message(code))
      case _ =>
        APIMessageError.BadRequest(ReplayApiErrorCode.message(code))
    }
}
