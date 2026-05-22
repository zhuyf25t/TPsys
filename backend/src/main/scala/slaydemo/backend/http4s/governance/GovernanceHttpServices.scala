package slaydemo.backend.http4s.governance

import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}

private[http4s] final case class GovernanceHttpServices(
  contributionAdjustmentService: ContributionAdjustmentService,
  notificationService: GovernanceNotificationService
)
