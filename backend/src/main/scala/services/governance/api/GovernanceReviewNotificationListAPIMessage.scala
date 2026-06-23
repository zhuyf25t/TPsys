package services.governance.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.governance.objects.GovernanceListLimit
import services.governance.services.GovernanceNotificationService
import system.api.APIMessageWithContext

final case class GovernanceReviewNotificationListAPIMessage(
  kind: Option[GovernanceReviewKindQuery],
  targetType: Option[GovernanceReviewTargetTypeQuery],
  limit: Option[GovernanceListLimit]
) extends APIMessageWithContext[GovernanceNotificationService, GovernanceReviewNotificationListResponse] {
  override def plan(
    service: GovernanceNotificationService,
    connection: Connection
  ): IO[GovernanceReviewNotificationListResponse] =
    GovernanceAPIPlanner.planReviewNotificationList(service, this)
}

object GovernanceReviewNotificationListAPIMessage {
  import GovernanceAPIMessageDecoding.given

  given Decoder[GovernanceReviewNotificationListAPIMessage] =
    deriveDecoder[GovernanceReviewNotificationListAPIMessage]
}
