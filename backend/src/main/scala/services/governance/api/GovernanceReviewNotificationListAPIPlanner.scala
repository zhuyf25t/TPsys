package services.governance.api

import cats.effect.IO

import services.governance.objects.GovernanceReviewNotificationRecord
import services.governance.services.GovernanceNotificationService

object GovernanceReviewNotificationListAPIPlanner {
  def plan(
    service: GovernanceNotificationService,
    message: GovernanceReviewNotificationListAPIMessage
  ): IO[GovernanceReviewNotificationListResponse] =
    for
      records <- listRecords(service, GovernanceQueryParsers.parseNotificationListRequest(message))
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
