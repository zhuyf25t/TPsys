package services.governance.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.governance.objects.GovernanceListLimit
import services.governance.services.ContributionAdjustmentService
import system.api.APIMessageWithContext

final case class ContributionAdjustmentListAPIMessage(
  limit: Option[GovernanceListLimit]
) extends APIMessageWithContext[ContributionAdjustmentService, ContributionAdjustmentListResponse] {
  override def plan(service: ContributionAdjustmentService, connection: Connection): IO[ContributionAdjustmentListResponse] =
    GovernanceAPIPlanner.planContributionAdjustmentList(service, this)
}

object ContributionAdjustmentListAPIMessage {
  import GovernanceAPIMessageDecoding.given

  given Decoder[ContributionAdjustmentListAPIMessage] =
    deriveDecoder[ContributionAdjustmentListAPIMessage]
}
