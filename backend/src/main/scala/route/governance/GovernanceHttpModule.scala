package route.governance

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.governance.api.{
  ContributionAdjustmentCreateAPIMessage,
  ContributionAdjustmentCreateResponse,
  ContributionAdjustmentListAPIMessage,
  ContributionAdjustmentListResponse,
  GovernanceAPIMessageSupport,
  GovernanceReviewNotificationCreateAPIMessage,
  GovernanceReviewNotificationCreateResponse,
  GovernanceReviewNotificationListAPIMessage,
  GovernanceReviewNotificationListResponse
}
import services.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object GovernanceHttpModule {
  def routes(services: GovernanceHttpServices): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          ContributionAdjustmentService,
          ContributionAdjustmentListAPIMessage,
          ContributionAdjustmentListResponse
        ](services.contributionAdjustmentService, GovernanceAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ContributionAdjustmentService,
          ContributionAdjustmentCreateAPIMessage,
          ContributionAdjustmentCreateResponse
        ](services.contributionAdjustmentService, GovernanceAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          GovernanceNotificationService,
          GovernanceReviewNotificationListAPIMessage,
          GovernanceReviewNotificationListResponse
        ](services.notificationService, GovernanceAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          GovernanceNotificationService,
          GovernanceReviewNotificationCreateAPIMessage,
          GovernanceReviewNotificationCreateResponse
        ](services.notificationService, GovernanceAPIMessageSupport.invalidJsonObject)
      )
    ) <+> GovernanceHttp4sRoutes.routes(services.contributionAdjustmentService, services.notificationService)
}
