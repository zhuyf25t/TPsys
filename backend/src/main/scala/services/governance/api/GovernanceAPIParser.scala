package services.governance.api

import services.governance.objects.GovernanceListLimit

object GovernanceAPIParser {
  def contributionAdjustmentListMessageFromQuery(query: Map[String, String]): ContributionAdjustmentListAPIMessage =
    ContributionAdjustmentListAPIMessage(
      limit = query.get("limit").flatMap(_.toIntOption).map(GovernanceListLimit.apply)
    )

  def reviewNotificationListMessageFromQuery(query: Map[String, String]): GovernanceReviewNotificationListAPIMessage =
    GovernanceReviewNotificationListAPIMessage(
      kind = Some(GovernanceReviewKindQuery.fromWire(query.get("kind"))),
      targetType = Some(GovernanceReviewTargetTypeQuery.fromWire(query.get("targetType"))),
      limit = query.get("limit").flatMap(_.toIntOption).map(GovernanceListLimit.apply)
    )
}
