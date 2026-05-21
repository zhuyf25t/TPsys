package slaydemo.backend.governance.objects.apiTypes

import slaydemo.backend.governance.objects.{GovernanceReviewKind, GovernanceReviewTargetType}

object GovernanceQueryParsers {
  def parseContributionAdjustmentLimit(query: Map[String, String]): Int =
    query.get("limit").flatMap(_.toIntOption).getOrElse(500)

  def parseNotificationListQuery(query: Map[String, String]): GovernanceNotificationListQueryParseResult = {
    val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(100)
    val rawKind = query.get("kind").map(_.trim).filter(_.nonEmpty)
    val rawTargetType = query.get("targetType").map(_.trim).filter(_.nonEmpty)
    val parsedKind = rawKind.flatMap(GovernanceReviewKind.fromWire)
    val parsedTargetType = rawTargetType.flatMap(GovernanceReviewTargetType.fromWire)

    if rawKind.nonEmpty && parsedKind.isEmpty then GovernanceNotificationListQueryParseResult.EmptyResults
    else if rawTargetType.nonEmpty && parsedTargetType.isEmpty then GovernanceNotificationListQueryParseResult.EmptyResults
    else
      GovernanceNotificationListQueryParseResult.Query(
        GovernanceNotificationListQuery(
          kind = parsedKind,
          targetType = parsedTargetType,
          limit = limit
        )
      )
  }
}

final case class GovernanceNotificationListQuery(
  kind: Option[GovernanceReviewKind],
  targetType: Option[GovernanceReviewTargetType],
  limit: Int
)

enum GovernanceNotificationListQueryParseResult {
  case Query(query: GovernanceNotificationListQuery)
  case EmptyResults
}
