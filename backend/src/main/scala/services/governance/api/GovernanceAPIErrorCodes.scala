package services.governance.api

enum GovernanceApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case InvalidActor
  case InvalidTarget
  case InvalidDelta
  case InvalidKind
  case InvalidBody
}

object GovernanceApiErrorCode {
  def fromContributionAdjustmentError(
    error: ContributionAdjustmentCommandParseError
  ): GovernanceApiErrorCode =
    error match {
      case ContributionAdjustmentCommandParseError.InvalidActor  => GovernanceApiErrorCode.InvalidActor
      case ContributionAdjustmentCommandParseError.InvalidTarget => GovernanceApiErrorCode.InvalidTarget
      case ContributionAdjustmentCommandParseError.InvalidDelta  => GovernanceApiErrorCode.InvalidDelta
    }

  def fromReviewNotificationError(
    error: GovernanceReviewNotificationCommandParseError
  ): GovernanceApiErrorCode =
    error match {
      case GovernanceReviewNotificationCommandParseError.InvalidKind   => GovernanceApiErrorCode.InvalidKind
      case GovernanceReviewNotificationCommandParseError.InvalidTarget => GovernanceApiErrorCode.InvalidTarget
      case GovernanceReviewNotificationCommandParseError.InvalidBody   => GovernanceApiErrorCode.InvalidBody
    }

  def wireValue(code: GovernanceApiErrorCode): String =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed  => "method_not_allowed"
      case GovernanceApiErrorCode.InvalidJsonObject => "bad_request"
      case GovernanceApiErrorCode.InvalidActor      => "invalid_actor"
      case GovernanceApiErrorCode.InvalidTarget     => "invalid_target"
      case GovernanceApiErrorCode.InvalidDelta      => "invalid_delta"
      case GovernanceApiErrorCode.InvalidKind       => "invalid_kind"
      case GovernanceApiErrorCode.InvalidBody       => "invalid_body"
    }

  def message(code: GovernanceApiErrorCode): String =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case GovernanceApiErrorCode.InvalidJsonObject => "Request body must be a JSON object."
      case _                                        => wireValue(code)
    }

  def statusCode(code: GovernanceApiErrorCode): Int =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed => 405
      case GovernanceApiErrorCode.InvalidActor     => 403
      case _                                       => 400
    }
}
