package slaydemo.backend.governance.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import slaydemo.backend.governance.objects.{GovernanceReviewKind, GovernanceReviewTargetType}

private[routes] object GovernanceQueryParsers {
  def parseContributionAdjustmentLimit(rawQuery: String): Int =
    queryParams(rawQuery).get("limit").flatMap(_.toIntOption).getOrElse(500)

  def parseNotificationListQuery(rawQuery: String): GovernanceNotificationListQueryParseResult = {
    val query = queryParams(rawQuery)
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

  private def queryParams(rawQuery: String): Map[String, String] =
    Option(rawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private[routes] final case class GovernanceNotificationListQuery(
  kind: Option[GovernanceReviewKind],
  targetType: Option[GovernanceReviewTargetType],
  limit: Int
)

private[routes] enum GovernanceNotificationListQueryParseResult {
  case Query(query: GovernanceNotificationListQuery)
  case EmptyResults
}
