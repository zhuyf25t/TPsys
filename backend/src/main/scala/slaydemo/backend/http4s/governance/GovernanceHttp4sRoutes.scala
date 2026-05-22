package slaydemo.backend.http4s.governance

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.governance.objects.apiTypes.{
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
import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.HttpApiErrors.apiError
import slaydemo.backend.http4s.Http4sCors.corsNoContent
import slaydemo.backend.http4s.Http4sEffects.blocking
import slaydemo.backend.http4s.Http4sRequestDecoders.decodeEntityBody
import slaydemo.backend.http4s.Http4sRequestPaths.requestPath
import slaydemo.backend.http4s.Http4sResponses.{errorResponse, jsonOk}

private[http4s] object GovernanceHttp4sRoutes {
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
    apiError(
      status = governanceApiStatus(code),
      code = GovernanceApiErrorCode.wireValue(code),
      message = GovernanceApiErrorCode.message(code)
    )

  private def governanceApiStatus(code: GovernanceApiErrorCode): Status =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed => Status.MethodNotAllowed
      case GovernanceApiErrorCode.InvalidActor     => Status.Forbidden
      case GovernanceApiErrorCode.InvalidJsonObject => Status.BadRequest
      case GovernanceApiErrorCode.InvalidTarget    => Status.BadRequest
      case GovernanceApiErrorCode.InvalidDelta     => Status.BadRequest
      case GovernanceApiErrorCode.InvalidKind      => Status.BadRequest
      case GovernanceApiErrorCode.InvalidBody      => Status.BadRequest
    }

}
