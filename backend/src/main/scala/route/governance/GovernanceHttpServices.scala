package route.governance

import services.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}

private[route] final case class GovernanceHttpServices(
  contributionAdjustmentService: ContributionAdjustmentService,
  notificationService: GovernanceNotificationService
)
