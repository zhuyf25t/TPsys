package services.governance.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.governance.objects.GovernanceReviewNotificationRecord
import services.governance.objects.apiTypes.{
  GovernanceReviewNotificationListApiRequest,
  GovernanceReviewNotificationListResponse
}
import services.governance.services.GovernanceNotificationService
import system.api.APIMessageWithContext

final case class GovernanceReviewNotificationListAPIMessage(
  request: GovernanceReviewNotificationListApiRequest
) extends APIMessageWithContext[GovernanceNotificationService, GovernanceReviewNotificationListResponse] {
  override def plan(
    service: GovernanceNotificationService,
    connection: Connection
  ): IO[GovernanceReviewNotificationListResponse] =
    for
      records <- listRecords(service, GovernanceQueryParsers.parseNotificationListRequest(request))
    yield GovernanceReviewNotificationListResponse.fromRecords(records)

  private def listRecords(
    service: GovernanceNotificationService,
    result: GovernanceNotificationListQueryParseResult
  ): IO[Vector[GovernanceReviewNotificationRecord]] =
    result match {
      case GovernanceNotificationListQueryParseResult.EmptyResults =>
        IO.pure(Vector.empty)
      case GovernanceNotificationListQueryParseResult.Query(query) =>
        service.listReviewNotifications(
          kind = query.kind,
          targetType = query.targetType,
          limit = query.limit
        )
    }
}

object GovernanceReviewNotificationListAPIMessage {
  given Decoder[GovernanceReviewNotificationListAPIMessage] =
    Decoder[GovernanceReviewNotificationListApiRequest].map(GovernanceReviewNotificationListAPIMessage.apply)
}
