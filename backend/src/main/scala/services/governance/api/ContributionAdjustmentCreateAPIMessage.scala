package services.governance.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.governance.objects.apiTypes.{ContributionAdjustmentApiRequest, ContributionAdjustmentCreateResponse}
import services.governance.services.ContributionAdjustmentService
import system.api.APIMessageWithContext

final case class ContributionAdjustmentCreateAPIMessage(
  request: ContributionAdjustmentApiRequest
) extends APIMessageWithContext[ContributionAdjustmentService, ContributionAdjustmentCreateResponse] {
  override def plan(service: ContributionAdjustmentService, connection: Connection): IO[ContributionAdjustmentCreateResponse] =
    for
      command <- IO.fromEither(
        GovernanceCommandParsers.parseContributionAdjustmentApiRequest(request).left.map(error =>
          GovernanceAPIMessageSupport.error(GovernanceApiErrorCode.fromContributionAdjustmentError(error))
        )
      )
      result <- IO.blocking(service.create(command))
    yield ContributionAdjustmentCreateResponse.fromResult(result)
}

object ContributionAdjustmentCreateAPIMessage {
  given Decoder[ContributionAdjustmentCreateAPIMessage] =
    Decoder[ContributionAdjustmentApiRequest].map(ContributionAdjustmentCreateAPIMessage.apply)
}
