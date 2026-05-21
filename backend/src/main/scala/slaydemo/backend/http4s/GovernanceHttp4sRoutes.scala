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
  GovernanceNotificationListQueryParseResult,
  GovernanceQueryParsers,
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

  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Method is not allowed.")
  private val RequestBodyJsonObjectError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = "Request body must be a JSON object.")
  private val InvalidActorError =
    HttpApiError(status = Status.Forbidden, code = "invalid_actor", message = "invalid_actor")
  private val InvalidTargetError =
    HttpApiError(status = Status.BadRequest, code = "invalid_target", message = "invalid_target")
  private val InvalidDeltaError =
    HttpApiError(status = Status.BadRequest, code = "invalid_delta", message = "invalid_delta")
  private val InvalidKindError =
    HttpApiError(status = Status.BadRequest, code = "invalid_kind", message = "invalid_kind")
  private val InvalidBodyError =
    HttpApiError(status = Status.BadRequest, code = "invalid_body", message = "invalid_body")

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
            IO.pure(apiError(MethodNotAllowedError))
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
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def listContributionAdjustments(
    request: Request[IO],
    service: ContributionAdjustmentService
  ): IO[Response[IO]] = {
    val limit = GovernanceQueryParsers.parseContributionAdjustmentLimit(request.params)
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
        IO.pure(apiError(RequestBodyJsonObjectError))
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
    GovernanceQueryParsers.parseNotificationListQuery(request.params) match {
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
        IO.pure(apiError(RequestBodyJsonObjectError))
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
    error match {
      case ContributionAdjustmentCommandParseError.InvalidActor  => InvalidActorError
      case ContributionAdjustmentCommandParseError.InvalidTarget => InvalidTargetError
      case ContributionAdjustmentCommandParseError.InvalidDelta  => InvalidDeltaError
    }

  private def reviewNotificationApiError(error: GovernanceReviewNotificationCommandParseError): HttpApiError =
    error match {
      case GovernanceReviewNotificationCommandParseError.InvalidKind   => InvalidKindError
      case GovernanceReviewNotificationCommandParseError.InvalidTarget => InvalidTargetError
      case GovernanceReviewNotificationCommandParseError.InvalidBody   => InvalidBodyError
    }

  private def path(request: Request[IO]): String =
    request.uri.path.renderString
}
