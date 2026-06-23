package services.governance.api

import services.governance.objects.*

object GovernanceQueryParsers {
  def parseContributionAdjustmentLimit(query: Map[String, String]): Int =
    query.get("limit").flatMap(_.toIntOption).getOrElse(500)

  def parseContributionAdjustmentLimitRequest(message: ContributionAdjustmentListAPIMessage): Int =
    message.limit.map(_.value).getOrElse(500)

  def parseNotificationListRequest(
    message: GovernanceReviewNotificationListAPIMessage
  ): GovernanceNotificationListQueryParseResult =
    val kind = message.kind.getOrElse(GovernanceReviewKindQuery.Missing)
    val targetType = message.targetType.getOrElse(GovernanceReviewTargetTypeQuery.Missing)
    (kind, targetType) match {
      case (GovernanceReviewKindQuery.Invalid, _) | (_, GovernanceReviewTargetTypeQuery.Invalid) =>
        GovernanceNotificationListQueryParseResult.EmptyResults
      case _ =>
        GovernanceNotificationListQueryParseResult.Query(
          GovernanceNotificationListQuery(
            kind = queryKind(kind),
            targetType = queryTargetType(targetType),
            limit = message.limit.map(_.value).getOrElse(100)
          )
        )
    }

  private def queryKind(value: GovernanceReviewKindQuery): Option[GovernanceReviewKind] =
    value match {
      case GovernanceReviewKindQuery.Valid(kind) => Some(kind)
      case GovernanceReviewKindQuery.Missing | GovernanceReviewKindQuery.Invalid => None
    }

  private def queryTargetType(value: GovernanceReviewTargetTypeQuery): Option[GovernanceReviewTargetType] =
    value match {
      case GovernanceReviewTargetTypeQuery.Valid(targetType) => Some(targetType)
      case GovernanceReviewTargetTypeQuery.Missing | GovernanceReviewTargetTypeQuery.Invalid => None
    }

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
