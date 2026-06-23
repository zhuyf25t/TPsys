package services.governance.api

import cats.effect.IO

import services.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}

object GovernanceAPIPlanner {
  def planContributionAdjustmentCreate(
    service: ContributionAdjustmentService,
    message: ContributionAdjustmentCreateAPIMessage
  ): IO[ContributionAdjustmentCreateResponse] =
    for
      command <- IO.fromEither(
        GovernanceCommandParsers
          .parseContributionAdjustmentMessage(message)
          .left
          .map(GovernanceAPIMessageErrors.contributionAdjustment)
      )
      result <- service.create(command)
    yield ContributionAdjustmentCreateResponse.fromResult(result)

  def planContributionAdjustmentList(
    service: ContributionAdjustmentService,
    message: ContributionAdjustmentListAPIMessage
  ): IO[ContributionAdjustmentListResponse] =
    for
      records <- service.list(GovernanceQueryParsers.parseContributionAdjustmentLimitRequest(message))
    yield ContributionAdjustmentListResponse.fromRecords(records)

  def planReviewNotificationCreate(
    service: GovernanceNotificationService,
    message: GovernanceReviewNotificationCreateAPIMessage
  ): IO[GovernanceReviewNotificationCreateResponse] =
    for
      command <- IO.fromEither(
        GovernanceCommandParsers
          .parseReviewNotificationMessage(message)
          .left
          .map(GovernanceAPIMessageErrors.reviewNotification)
      )
      result <- service.createReviewNotification(command)
    yield GovernanceReviewNotificationCreateResponse.fromResult(result)

  def planReviewNotificationList(
    service: GovernanceNotificationService,
    message: GovernanceReviewNotificationListAPIMessage
  ): IO[GovernanceReviewNotificationListResponse] =
    GovernanceReviewNotificationListAPIPlanner.plan(service, message)
}
