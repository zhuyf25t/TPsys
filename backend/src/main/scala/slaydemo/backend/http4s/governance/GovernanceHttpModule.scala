package slaydemo.backend.http4s.governance

import cats.effect.IO
import org.http4s.HttpRoutes

import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}

private[http4s] object GovernanceHttpModule {
  def routes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    GovernanceHttp4sRoutes.routes(contributionAdjustmentService, notificationService)
}
