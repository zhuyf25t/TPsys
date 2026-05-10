package slaydemo.backend.replay.routes

import slaydemo.backend.replay.services.{ReplayCommentError, ReplayRecordError}

private[routes] final case class ReplayRouteError(
  status: Int,
  code: String,
  message: String
)

private[routes] object ReplayRouteErrorMapper {
  def methodNotAllowed: ReplayRouteError =
    ReplayRouteError(405, "method_not_allowed", "Method is not allowed.")

  def replayNotFound: ReplayRouteError =
    ReplayRouteError(404, "replay_not_found", "replay_not_found")

  def invalidReplayId: ReplayRouteError =
    ReplayRouteError(400, "invalid_replay_id", "invalid_replay_id")

  def badRequest(message: String): ReplayRouteError =
    ReplayRouteError(400, "bad_request", message)

  def target(target: ReplayTarget): Option[ReplayRouteError] =
    target match {
      case ReplayTarget.Invalid =>
        Some(replayNotFound)
      case ReplayTarget.InvalidReplayId =>
        Some(invalidReplayId)
      case ReplayTarget.Collection | ReplayTarget.Detail(_) | ReplayTarget.Comments(_) =>
        None
    }

  def recordParse(error: ReplayRecordCommandParseError): ReplayRouteError =
    error match {
      case ReplayRecordCommandParseError.InvalidReplayId =>
        invalidReplayId
      case ReplayRecordCommandParseError.InvalidBattleId =>
        ReplayRouteError(400, "invalid_battle_id", "invalid_battle_id")
      case ReplayRecordCommandParseError.InvalidHandle =>
        ReplayRouteError(400, "invalid_handle", "invalid_handle")
      case ReplayRecordCommandParseError.VisitorNotAllowed =>
        ReplayRouteError(403, "visitor_not_allowed", "visitor_not_allowed")
    }

  def record(error: ReplayRecordError): ReplayRouteError =
    error match {
      case ReplayRecordError.InvalidReplayId =>
        invalidReplayId
      case ReplayRecordError.InvalidFramesJson =>
        ReplayRouteError(400, "invalid_frames_json", "invalid_frames_json")
    }

  def commentParse(error: ReplayCommentCommandParseError): ReplayRouteError =
    error match {
      case ReplayCommentCommandParseError.InvalidReplayId =>
        invalidReplayId
      case ReplayCommentCommandParseError.InvalidAuthorHandle =>
        ReplayRouteError(400, "invalid_author_handle", "invalid_author_handle")
      case ReplayCommentCommandParseError.VisitorNotAllowed =>
        ReplayRouteError(403, "visitor_not_allowed", "visitor_not_allowed")
    }

  def comment(error: ReplayCommentError): ReplayRouteError =
    error match {
      case ReplayCommentError.InvalidReplayId =>
        invalidReplayId
      case ReplayCommentError.ReplayNotFound =>
        replayNotFound
      case ReplayCommentError.InvalidAuthor =>
        ReplayRouteError(403, "visitor_not_allowed", "visitor_not_allowed")
      case ReplayCommentError.InvalidBody =>
        ReplayRouteError(400, "invalid_body", "invalid_body")
    }
}
