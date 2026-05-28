package services.replay.api

import services.replay.services.{ReplayCommentError, ReplayRecordError}

enum ReplayApiErrorCode {
  case MethodNotAllowed
  case BadJsonObject
  case ReplayNotFound
  case InvalidReplayId
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
  case InvalidFramesJson
  case InvalidAuthorHandle
  case InvalidBody
}

object ReplayApiErrorCode {
  def wireValue(code: ReplayApiErrorCode): String =
    code match {
      case ReplayApiErrorCode.MethodNotAllowed    => "method_not_allowed"
      case ReplayApiErrorCode.BadJsonObject       => "bad_request"
      case ReplayApiErrorCode.ReplayNotFound      => "replay_not_found"
      case ReplayApiErrorCode.InvalidReplayId     => "invalid_replay_id"
      case ReplayApiErrorCode.InvalidBattleId     => "invalid_battle_id"
      case ReplayApiErrorCode.InvalidHandle       => "invalid_handle"
      case ReplayApiErrorCode.VisitorNotAllowed   => "visitor_not_allowed"
      case ReplayApiErrorCode.InvalidFramesJson   => "invalid_frames_json"
      case ReplayApiErrorCode.InvalidAuthorHandle => "invalid_author_handle"
      case ReplayApiErrorCode.InvalidBody         => "invalid_body"
    }

  def message(code: ReplayApiErrorCode): String =
    code match {
      case ReplayApiErrorCode.MethodNotAllowed => "Method is not allowed."
      case ReplayApiErrorCode.BadJsonObject    => "Request body must be a JSON object."
      case _                                   => wireValue(code)
    }

  def statusCode(code: ReplayApiErrorCode): Int =
    code match {
      case ReplayApiErrorCode.MethodNotAllowed  => 405
      case ReplayApiErrorCode.VisitorNotAllowed => 403
      case ReplayApiErrorCode.ReplayNotFound    => 404
      case _                                    => 400
    }
}

object ReplayApiErrorMapper {
  def recordDecodeErrorCode(error: ReplayRecordDecodeError): ReplayApiErrorCode =
    error match {
      case ReplayRecordDecodeError.BadJsonObject     => ReplayApiErrorCode.BadJsonObject
      case ReplayRecordDecodeError.InvalidReplayId   => ReplayApiErrorCode.InvalidReplayId
      case ReplayRecordDecodeError.InvalidBattleId   => ReplayApiErrorCode.InvalidBattleId
      case ReplayRecordDecodeError.InvalidHandle     => ReplayApiErrorCode.InvalidHandle
      case ReplayRecordDecodeError.VisitorNotAllowed => ReplayApiErrorCode.VisitorNotAllowed
    }

  def recordServiceErrorCode(error: ReplayRecordError): ReplayApiErrorCode =
    error match {
      case ReplayRecordError.InvalidReplayId   => ReplayApiErrorCode.InvalidReplayId
      case ReplayRecordError.InvalidFramesJson => ReplayApiErrorCode.InvalidFramesJson
    }

  def commentDecodeErrorCode(error: ReplayCommentDecodeError): ReplayApiErrorCode =
    error match {
      case ReplayCommentDecodeError.BadJsonObject       => ReplayApiErrorCode.BadJsonObject
      case ReplayCommentDecodeError.InvalidReplayId     => ReplayApiErrorCode.InvalidReplayId
      case ReplayCommentDecodeError.InvalidAuthorHandle => ReplayApiErrorCode.InvalidAuthorHandle
      case ReplayCommentDecodeError.VisitorNotAllowed   => ReplayApiErrorCode.VisitorNotAllowed
    }

  def commentServiceErrorCode(error: ReplayCommentError): ReplayApiErrorCode =
    error match {
      case ReplayCommentError.InvalidReplayId => ReplayApiErrorCode.InvalidReplayId
      case ReplayCommentError.ReplayNotFound  => ReplayApiErrorCode.ReplayNotFound
      case ReplayCommentError.InvalidAuthor   => ReplayApiErrorCode.VisitorNotAllowed
      case ReplayCommentError.InvalidBody     => ReplayApiErrorCode.InvalidBody
    }
}
