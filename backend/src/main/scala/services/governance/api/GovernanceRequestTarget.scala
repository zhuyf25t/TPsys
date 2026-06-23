package services.governance.api

object GovernanceRequestTarget {
  private val ContributionAdjustmentPaths: Set[String] =
    Set("/governance/contribution-adjustments", "/api/governance/contribution-adjustments")
  private val AdminNotificationPaths: Set[String] =
    Set("/governance/admin-notifications", "/api/governance/admin-notifications")

  def isContributionAdjustmentPath(path: String): Boolean =
    ContributionAdjustmentPaths.contains(path)

  def isAdminNotificationPath(path: String): Boolean =
    AdminNotificationPaths.contains(path)

  def contributionAdjustmentLimitFromQuery(query: Map[String, String]): Int =
    GovernanceQueryParsers.parseContributionAdjustmentLimit(query)

  def notificationListFromQuery(query: Map[String, String]): GovernanceNotificationListQueryParseResult =
    GovernanceQueryParsers.parseNotificationListQuery(query)
}
