package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
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
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}

private[http4s] object GovernanceHttp4sRoutes {
  import CirceEntityDecoder.*
  import CirceEntityEncoder.*

  def routes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if GovernanceRequestTarget.isContributionAdjustmentPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            listContributionAdjustments(request, contributionAdjustmentService)
          case Method.POST =>
            createContributionAdjustment(request, contributionAdjustmentService)
          case _ =>
            IO.pure(apiError(governanceApiError(GovernanceApiErrorCode.MethodNotAllowed)))
        }
      case request if GovernanceRequestTarget.isAdminNotificationPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            listAdminNotifications(request, notificationService)
          case Method.POST =>
            createAdminNotification(request, notificationService)
          case _ =>
            IO.pure(apiError(governanceApiError(GovernanceApiErrorCode.MethodNotAllowed)))
        }
    }

  private def listContributionAdjustments(
    request: Request[IO],
    service: ContributionAdjustmentService
  ): IO[Response[IO]] = {
    val limit = GovernanceRequestTarget.contributionAdjustmentLimitFromQuery(request.params)
    blocking(service.list(limit)).flatMap(records =>
      Ok(ContributionAdjustmentListResponse.fromRecords(records).asJson).map(withCors)
    )
  }

  private def createContributionAdjustment(
    request: Request[IO],
    service: ContributionAdjustmentService
  ): IO[Response[IO]] =
    request.as[ContributionAdjustmentApiRequest].attempt.flatMap {
      case Left(_) =>
        IO.pure(apiError(governanceApiError(GovernanceApiErrorCode.InvalidJsonObject)))
      case Right(parsedRequest) =>
        parsedRequest.toCommand match {
          case Left(error) =>
            IO.pure(apiError(contributionAdjustmentApiError(error)))
          case Right(command) =>
            blocking(service.create(command)).map(result =>
              withCors(Response[IO](Status.Ok).withEntity(ContributionAdjustmentCreateResponse.fromResult(result).asJson))
            )
        }
    }

  private def listAdminNotifications(
    request: Request[IO],
    service: GovernanceNotificationService
  ): IO[Response[IO]] =
    GovernanceRequestTarget.notificationListFromQuery(request.params) match {
      case GovernanceNotificationListQueryParseResult.EmptyResults =>
        Ok(GovernanceReviewNotificationListResponse.fromRecords(Vector.empty).asJson).map(withCors)
      case GovernanceNotificationListQueryParseResult.Query(query) =>
        blocking(
          service.listReviewNotifications(
            kind = query.kind,
            targetType = query.targetType,
            limit = query.limit
          )
        ).flatMap(records => Ok(GovernanceReviewNotificationListResponse.fromRecords(records).asJson).map(withCors))
    }

  private def createAdminNotification(
    request: Request[IO],
    service: GovernanceNotificationService
  ): IO[Response[IO]] =
    request.as[GovernanceReviewNotificationApiRequest].attempt.flatMap {
      case Left(_) =>
        IO.pure(apiError(governanceApiError(GovernanceApiErrorCode.InvalidJsonObject)))
      case Right(parsedRequest) =>
        parsedRequest.toCommand match {
          case Left(error) =>
            IO.pure(apiError(reviewNotificationApiError(error)))
          case Right(command) =>
            blocking(service.createReviewNotification(command)).map(result =>
              withCors(
                Response[IO](Status.Ok).withEntity(
                  GovernanceReviewNotificationCreateResponse.fromResult(result).asJson
                )
              )
            )
        }
    }

  private def contributionAdjustmentApiError(error: ContributionAdjustmentCommandParseError): HttpApiError =
    governanceApiError(GovernanceApiErrorCode.fromContributionAdjustmentError(error))

  private def reviewNotificationApiError(error: GovernanceReviewNotificationCommandParseError): HttpApiError =
    governanceApiError(GovernanceApiErrorCode.fromReviewNotificationError(error))

  private def governanceApiError(code: GovernanceApiErrorCode): HttpApiError =
    HttpApiError(
      status = statusFrom(GovernanceApiErrorCode.statusCode(code)),
      code = GovernanceApiErrorCode.wireValue(code),
      message = GovernanceApiErrorCode.message(code)
    )

  private def statusFrom(value: Int): Status =
    value match {
      case 400 => Status.BadRequest
      case 403 => Status.Forbidden
      case 405 => Status.MethodNotAllowed
      case _   => Status.InternalServerError
    }

  private def path(request: Request[IO]): String =
    request.uri.path.renderString
}
