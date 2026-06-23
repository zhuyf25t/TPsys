package route.governance

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, MessageFailure, Method, Request, Response}

import services.governance.api.{
  ContributionAdjustmentCreateAPIMessage,
  ContributionAdjustmentCreateResponse,
  ContributionAdjustmentListAPIMessage,
  ContributionAdjustmentListResponse,
  GovernanceAPIMessageSupport,
  GovernanceAPIParser,
  GovernanceApiErrorCode,
  GovernanceRequestTarget,
  GovernanceReviewNotificationCreateAPIMessage,
  GovernanceReviewNotificationCreateResponse,
  GovernanceReviewNotificationListAPIMessage,
  GovernanceReviewNotificationListResponse
}
import services.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import system.api.{APIMessage, APIMessageError, APIMessageRouter}
import system.api.APIMessageRouter.APIMessageRequestAlias
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object GovernanceHttp4sRoutes {
  def routes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    postAliasRoutes(contributionAdjustmentService, notificationService) <+>
      getAliasRoutes(contributionAdjustmentService, notificationService) <+>
      compatibilityRoutes

  private def postAliasRoutes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    APIMessageRouter.aliasRoutes(
      apiMessages = List(
        apiWithContext[
          ContributionAdjustmentService,
          ContributionAdjustmentCreateAPIMessage,
          ContributionAdjustmentCreateResponse
        ](contributionAdjustmentService, GovernanceAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          GovernanceNotificationService,
          GovernanceReviewNotificationCreateAPIMessage,
          GovernanceReviewNotificationCreateResponse
        ](notificationService, GovernanceAPIMessageSupport.invalidJsonObject)
      ),
      pathAliases = Map(
        "/governance/contribution-adjustments" -> APIMessage.apiNameFromClass[ContributionAdjustmentCreateAPIMessage],
        "/api/governance/contribution-adjustments" -> APIMessage.apiNameFromClass[ContributionAdjustmentCreateAPIMessage],
        "/governance/admin-notifications" -> APIMessage.apiNameFromClass[GovernanceReviewNotificationCreateAPIMessage],
        "/api/governance/admin-notifications" -> APIMessage.apiNameFromClass[GovernanceReviewNotificationCreateAPIMessage]
      ),
      responseTransform = withCors,
      errorHandler = governanceAPIMessageErrorResponse
    )

  private def getAliasRoutes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = List(
        apiWithContext[
          ContributionAdjustmentService,
          ContributionAdjustmentListAPIMessage,
          ContributionAdjustmentListResponse
        ](contributionAdjustmentService, GovernanceAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          GovernanceNotificationService,
          GovernanceReviewNotificationListAPIMessage,
          GovernanceReviewNotificationListResponse
        ](notificationService, GovernanceAPIMessageSupport.invalidJsonObject)
      ),
      aliasForRequest = governanceGetAlias(contributionAdjustmentService, notificationService),
      errorHandler = governanceAPIMessageErrorResponse
    )

  private def governanceGetAlias(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  )(request: Request[IO]): Option[APIMessageRequestAlias] =
    if request.method != Method.GET then None
    else if GovernanceRequestTarget.isContributionAdjustmentPath(requestPath(request)) then
      Some(
        APIMessageRequestAlias.fromContextMessage[
          ContributionAdjustmentService,
          ContributionAdjustmentListAPIMessage,
          ContributionAdjustmentListResponse
        ](
          context = contributionAdjustmentService,
          message = GovernanceAPIParser.contributionAdjustmentListMessageFromQuery(request.params),
          responseTransform = withCors
        )
      )
    else if GovernanceRequestTarget.isAdminNotificationPath(requestPath(request)) then
      Some(
        APIMessageRequestAlias.fromContextMessage[
          GovernanceNotificationService,
          GovernanceReviewNotificationListAPIMessage,
          GovernanceReviewNotificationListResponse
        ](
          context = notificationService,
          message = GovernanceAPIParser.reviewNotificationListMessageFromQuery(request.params),
          responseTransform = withCors
        )
      )
    else None

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if GovernanceRequestTarget.isContributionAdjustmentPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(governanceApiError(GovernanceApiErrorCode.MethodNotAllowed))
        }
      case request if GovernanceRequestTarget.isAdminNotificationPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(governanceApiError(GovernanceApiErrorCode.MethodNotAllowed))
        }
    }

  private def governanceAPIMessageErrorResponse: PartialFunction[Throwable, IO[Response[IO]]] = {
    case _: MessageFailure =>
      errorResponse(governanceApiError(GovernanceApiErrorCode.InvalidJsonObject))
    case error: APIMessageError =>
      errorResponse(governanceApiError(governanceApiErrorCode(error)))
  }

  private def governanceApiError(code: GovernanceApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = GovernanceApiErrorCode.statusCode(code),
      code = GovernanceApiErrorCode.wireValue(code),
      message = GovernanceApiErrorCode.message(code)
    )

  private def governanceApiErrorCode(error: APIMessageError): GovernanceApiErrorCode =
    GovernanceApiErrorCode.values
      .find(code =>
        error.getMessage == GovernanceApiErrorCode.message(code) ||
          error.getMessage == GovernanceApiErrorCode.wireValue(code)
      )
      .getOrElse(GovernanceApiErrorCode.InvalidJsonObject)

}
