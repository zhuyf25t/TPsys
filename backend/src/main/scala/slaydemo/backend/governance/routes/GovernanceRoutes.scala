package slaydemo.backend.governance.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.governance.services.*
import slaydemo.backend.shared.routes.HttpRouteSupport

final class GovernanceRoutes(
  contributionAdjustmentService: ContributionAdjustmentService,
  notificationService: GovernanceNotificationService
) {
  def contributionAdjustments(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          val limit = GovernanceQueryParsers.parseContributionAdjustmentLimit(exchange.getRequestURI.getRawQuery)
          HttpRouteSupport.sendJson(exchange, 200, GovernanceRouteJsonRenderer.renderAdjustments(contributionAdjustmentService.list(limit)))
        case "POST" =>
          GovernanceRequestBodyParser.parseContributionAdjustmentBody(HttpRouteSupport.readRequestBody(exchange)) match {
            case Left(message) =>
              jsonError(exchange, 400, "bad_request", message)
            case Right(request) =>
              GovernanceCommandParsers.parseContributionAdjustmentCommand(request) match {
                case Left(ContributionAdjustmentCommandParseError.InvalidActor) =>
                  jsonError(exchange, 403, "invalid_actor", "invalid_actor")
                case Left(ContributionAdjustmentCommandParseError.InvalidTarget) =>
                  jsonError(exchange, 400, "invalid_target", "invalid_target")
                case Left(ContributionAdjustmentCommandParseError.InvalidDelta) =>
                  jsonError(exchange, 400, "invalid_delta", "invalid_delta")
                case Right(command) =>
                  val result = contributionAdjustmentService.create(command)
                  HttpRouteSupport.sendJson(exchange, 200, GovernanceRouteJsonRenderer.renderAdjustmentResult(result))
              }
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  def adminNotifications(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          val records = GovernanceQueryParsers.parseNotificationListQuery(exchange.getRequestURI.getRawQuery) match {
            case GovernanceNotificationListQueryParseResult.EmptyResults =>
              Vector.empty
            case GovernanceNotificationListQueryParseResult.Query(query) =>
              notificationService.listReviewNotifications(
                kind = query.kind,
                targetType = query.targetType,
                limit = query.limit
              )
          }
          HttpRouteSupport.sendJson(exchange, 200, GovernanceRouteJsonRenderer.renderNotifications(records))
        case "POST" =>
          GovernanceRequestBodyParser.parseReviewNotificationBody(HttpRouteSupport.readRequestBody(exchange)) match {
            case Left(message) =>
              jsonError(exchange, 400, "bad_request", message)
            case Right(request) =>
              GovernanceCommandParsers.parseReviewNotificationCommand(request) match {
                case Left(GovernanceReviewNotificationCommandParseError.InvalidKind) =>
                  jsonError(exchange, 400, "invalid_kind", "invalid_kind")
                case Left(GovernanceReviewNotificationCommandParseError.InvalidTarget) =>
                  jsonError(exchange, 400, "invalid_target", "invalid_target")
                case Left(GovernanceReviewNotificationCommandParseError.InvalidBody) =>
                  jsonError(exchange, 400, "invalid_body", "invalid_body")
                case Right(command) =>
                  val result = notificationService.createReviewNotification(command)
                  HttpRouteSupport.sendJson(exchange, 200, GovernanceRouteJsonRenderer.renderNotificationResult(result))
              }
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

}

object GovernanceRoutes {
  def apply(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): GovernanceRoutes =
    new GovernanceRoutes(contributionAdjustmentService, notificationService)
}
