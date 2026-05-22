package route.governance

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{HttpRoutes, Method, Request, Response}

import services.governance.objects.apiTypes.{
  ContributionAdjustmentApiRequest,
  ContributionAdjustmentCommandParseError,
  ContributionAdjustmentCreateResponse,
  ContributionAdjustmentListResponse,
  GovernanceApiErrorCode,
  GovernanceNotificationListQueryParseResult,
  GovernanceRequestTarget,
  GovernanceReviewNotificationApiRequest,
  GovernanceReviewNotificationCommandParseError,
  GovernanceReviewNotificationCreateResponse,
  GovernanceReviewNotificationListResponse
}
import services.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.corsNoContent
import route.Http4sEffects.blocking
import route.Http4sRequestDecoders.decodeEntityBody
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.{errorResponse, jsonOk}

private[route] object GovernanceHttp4sRoutes {
  import CirceEntityDecoder.*

  def routes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if GovernanceRequestTarget.isContributionAdjustmentPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            listContributionAdjustments(request, contributionAdjustmentService)
          case Method.POST =>
            createContributionAdjustment(request, contributionAdjustmentService)
          case _ =>
            errorResponse(governanceApiError(GovernanceApiErrorCode.MethodNotAllowed))
        }
      case request if GovernanceRequestTarget.isAdminNotificationPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            listAdminNotifications(request, notificationService)
          case Method.POST =>
            createAdminNotification(request, notificationService)
          case _ =>
            errorResponse(governanceApiError(GovernanceApiErrorCode.MethodNotAllowed))
        }
    }

  private def listContributionAdjustments(
    request: Request[IO],
    service: ContributionAdjustmentService
  ): IO[Response[IO]] = {
    val limit = GovernanceRequestTarget.contributionAdjustmentLimitFromQuery(request.params)
    blocking(service.list(limit)).flatMap(records =>
      jsonOk(ContributionAdjustmentListResponse.fromRecords(records).asJson)
    )
  }

  private def createContributionAdjustment(
    request: Request[IO],
    service: ContributionAdjustmentService
  ): IO[Response[IO]] =
    decodeEntityBody[GovernanceApiErrorCode, ContributionAdjustmentApiRequest](
      request,
      GovernanceApiErrorCode.InvalidJsonObject
    ).flatMap {
      case Left(errorCode) =>
        errorResponse(governanceApiError(errorCode))
      case Right(parsedRequest) =>
        parsedRequest.toCommand match {
          case Left(error) =>
            errorResponse(contributionAdjustmentApiError(error))
          case Right(command) =>
            blocking(service.create(command)).flatMap(result =>
              jsonOk(ContributionAdjustmentCreateResponse.fromResult(result).asJson)
            )
        }
    }

  private def listAdminNotifications(
    request: Request[IO],
    service: GovernanceNotificationService
  ): IO[Response[IO]] =
    GovernanceRequestTarget.notificationListFromQuery(request.params) match {
      case GovernanceNotificationListQueryParseResult.EmptyResults =>
        jsonOk(GovernanceReviewNotificationListResponse.fromRecords(Vector.empty).asJson)
      case GovernanceNotificationListQueryParseResult.Query(query) =>
        blocking(
          service.listReviewNotifications(
            kind = query.kind,
            targetType = query.targetType,
            limit = query.limit
          )
        ).flatMap(records => jsonOk(GovernanceReviewNotificationListResponse.fromRecords(records).asJson))
    }

  private def createAdminNotification(
    request: Request[IO],
    service: GovernanceNotificationService
  ): IO[Response[IO]] =
    decodeEntityBody[GovernanceApiErrorCode, GovernanceReviewNotificationApiRequest](
      request,
      GovernanceApiErrorCode.InvalidJsonObject
    ).flatMap {
      case Left(errorCode) =>
        errorResponse(governanceApiError(errorCode))
      case Right(parsedRequest) =>
        parsedRequest.toCommand match {
          case Left(error) =>
            errorResponse(reviewNotificationApiError(error))
          case Right(command) =>
            blocking(service.createReviewNotification(command)).flatMap(result =>
              jsonOk(GovernanceReviewNotificationCreateResponse.fromResult(result).asJson)
            )
        }
    }

  private def contributionAdjustmentApiError(error: ContributionAdjustmentCommandParseError): HttpApiError =
    governanceApiError(GovernanceApiErrorCode.fromContributionAdjustmentError(error))

  private def reviewNotificationApiError(error: GovernanceReviewNotificationCommandParseError): HttpApiError =
    governanceApiError(GovernanceApiErrorCode.fromReviewNotificationError(error))

  private def governanceApiError(code: GovernanceApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = GovernanceApiErrorCode.statusCode(code),
      code = GovernanceApiErrorCode.wireValue(code),
      message = GovernanceApiErrorCode.message(code)
    )

}
