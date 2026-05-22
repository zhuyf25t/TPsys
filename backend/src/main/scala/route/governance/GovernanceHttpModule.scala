package route.governance

import cats.effect.IO
import org.http4s.HttpRoutes

private[route] object GovernanceHttpModule {
  def routes(services: GovernanceHttpServices): HttpRoutes[IO] =
    GovernanceHttp4sRoutes.routes(services.contributionAdjustmentService, services.notificationService)
}
