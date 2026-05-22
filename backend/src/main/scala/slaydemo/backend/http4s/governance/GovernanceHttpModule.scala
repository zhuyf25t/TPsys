package slaydemo.backend.http4s.governance

import cats.effect.IO
import org.http4s.HttpRoutes

private[http4s] object GovernanceHttpModule {
  def routes(services: GovernanceHttpServices): HttpRoutes[IO] =
    GovernanceHttp4sRoutes.routes(services.contributionAdjustmentService, services.notificationService)
}
