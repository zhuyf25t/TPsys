package services.governance.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.governance.objects.{
  GovernanceActorHandle,
  GovernanceReviewTargetPath,
  GovernanceReviewTargetTitle
}
import services.governance.services.GovernanceNotificationService
import system.api.APIMessageWithContext

final case class GovernanceReviewNotificationCreateAPIMessage(
  actorHandle: Option[GovernanceActorHandle],
  kind: GovernanceReviewKindInput,
  targetType: GovernanceReviewTargetTypeInput,
  targetId: GovernanceReviewTargetIdInput,
  targetTitle: Option[GovernanceReviewTargetTitle],
  targetPath: Option[GovernanceReviewTargetPath],
  body: GovernanceReviewBodyInput
) extends APIMessageWithContext[GovernanceNotificationService, GovernanceReviewNotificationCreateResponse] {
  override def plan(
    service: GovernanceNotificationService,
    connection: Connection
  ): IO[GovernanceReviewNotificationCreateResponse] =
    GovernanceAPIPlanner.planReviewNotificationCreate(service, this)
}

object GovernanceReviewNotificationCreateAPIMessage {
  import GovernanceAPIMessageDecoding.given

  given Decoder[GovernanceReviewNotificationCreateAPIMessage] =
    deriveDecoder[GovernanceReviewNotificationCreateAPIMessage]
}
