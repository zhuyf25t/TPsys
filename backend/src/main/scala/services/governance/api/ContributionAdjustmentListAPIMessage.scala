package services.governance.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.governance.objects.apiTypes.{ContributionAdjustmentListApiRequest, ContributionAdjustmentListResponse}
import services.governance.services.ContributionAdjustmentService
import system.api.APIMessageWithContext

final case class ContributionAdjustmentListAPIMessage(
  request: ContributionAdjustmentListApiRequest
) extends APIMessageWithContext[ContributionAdjustmentService, ContributionAdjustmentListResponse] {
  override def plan(service: ContributionAdjustmentService, connection: Connection): IO[ContributionAdjustmentListResponse] =
    for
      records <- service.list(GovernanceQueryParsers.parseContributionAdjustmentLimitRequest(request))
    yield ContributionAdjustmentListResponse.fromRecords(records)
}

object ContributionAdjustmentListAPIMessage {
  given Decoder[ContributionAdjustmentListAPIMessage] =
    Decoder[ContributionAdjustmentListApiRequest].map(ContributionAdjustmentListAPIMessage.apply)
}
