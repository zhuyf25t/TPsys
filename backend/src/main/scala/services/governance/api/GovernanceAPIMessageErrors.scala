package services.governance.api

import system.api.APIMessageError

private[api] object GovernanceAPIMessageErrors {
  def contributionAdjustment(error: ContributionAdjustmentCommandParseError): APIMessageError =
    GovernanceAPIMessageSupport.error(GovernanceApiErrorCode.fromContributionAdjustmentError(error))

  def reviewNotification(error: GovernanceReviewNotificationCommandParseError): APIMessageError =
    GovernanceAPIMessageSupport.error(GovernanceApiErrorCode.fromReviewNotificationError(error))
}
