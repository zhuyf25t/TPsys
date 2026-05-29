package services.governance.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.governance.objects.apiTypes.{GovernanceReviewNotificationApiRequest, GovernanceReviewNotificationCreateResponse}
import services.governance.services.GovernanceNotificationService
import system.api.APIMessageWithContext

final case class GovernanceReviewNotificationCreateAPIMessage(
  request: GovernanceReviewNotificationApiRequest
) extends APIMessageWithContext[GovernanceNotificationService, GovernanceReviewNotificationCreateResponse] {
  override def plan(
    service: GovernanceNotificationService,
    connection: Connection
  ): IO[GovernanceReviewNotificationCreateResponse] =
    for
      command <- IO.fromEither(
        GovernanceCommandParsers.parseReviewNotificationApiRequest(request).left.map(error =>
          GovernanceAPIMessageSupport.error(GovernanceApiErrorCode.fromReviewNotificationError(error))
        )
      )
      result <- service.createReviewNotification(command)
    yield GovernanceReviewNotificationCreateResponse.fromResult(result)
}

object GovernanceReviewNotificationCreateAPIMessage {
  given Decoder[GovernanceReviewNotificationCreateAPIMessage] =
    Decoder[GovernanceReviewNotificationApiRequest].map(GovernanceReviewNotificationCreateAPIMessage.apply)
}
