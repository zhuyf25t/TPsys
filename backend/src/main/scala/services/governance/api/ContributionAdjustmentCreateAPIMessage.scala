package services.governance.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.governance.objects.{GovernanceReason, GovernanceSourceLabel, GovernanceSourcePath}
import services.governance.services.ContributionAdjustmentService
import system.api.APIMessageWithContext

final case class ContributionAdjustmentCreateAPIMessage(
  actorHandle: GovernanceAdminInput,
  targetHandle: GovernanceAdjustmentTargetInput,
  delta: ContributionDeltaInput,
  reason: Option[GovernanceReason],
  sourceLabel: Option[GovernanceSourceLabel],
  sourcePath: Option[GovernanceSourcePath]
) extends APIMessageWithContext[ContributionAdjustmentService, ContributionAdjustmentCreateResponse] {
  override def plan(service: ContributionAdjustmentService, connection: Connection): IO[ContributionAdjustmentCreateResponse] =
    GovernanceAPIPlanner.planContributionAdjustmentCreate(service, this)
}

object ContributionAdjustmentCreateAPIMessage {
  import GovernanceAPIMessageDecoding.given

  given Decoder[ContributionAdjustmentCreateAPIMessage] =
    deriveDecoder[ContributionAdjustmentCreateAPIMessage]
}
