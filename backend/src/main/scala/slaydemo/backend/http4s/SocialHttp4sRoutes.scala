package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.social.objects.apiTypes.{
  FriendRequestCreateApiRequest,
  FriendRequestCreateResponse,
  FriendRequestListResponse,
  FriendRequestOwnerQuery,
  FriendRequestRespondApiRequest,
  FriendRequestRespondResponse,
  SocialRequestTarget,
  SocialRouteCreateError,
  SocialRouteHandleError,
  SocialRouteRespondError
}
import slaydemo.backend.social.services.{FriendRequestCreateError, FriendRequestRespondError, FriendRequestService}

private[http4s] object SocialHttp4sRoutes {
  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Method is not allowed.")
  private val MissingOwnerError =
    HttpApiError(status = Status.BadRequest, code = "missing_owner", message = "missing_owner")
  private val VisitorNotAllowedError =
    HttpApiError(status = Status.Forbidden, code = "visitor_not_allowed", message = "visitor_not_allowed")
  private val InvalidOwnerError =
    HttpApiError(status = Status.BadRequest, code = "invalid_owner", message = "invalid_owner")
  private val InvalidHandlesError =
    HttpApiError(status = Status.BadRequest, code = "invalid_handles", message = "invalid_handles")
  private val RequestNotFoundError =
    HttpApiError(status = Status.NotFound, code = "request_not_found", message = "request_not_found")
  private val ForbiddenError =
    HttpApiError(status = Status.Forbidden, code = "forbidden", message = "forbidden")
  private val InvalidDecisionError =
    HttpApiError(status = Status.BadRequest, code = "invalid_decision", message = "invalid_decision")
  private val MissingFieldsError =
    HttpApiError(status = Status.BadRequest, code = "missing_fields", message = "missing_fields")
  private val InvalidActorError =
    HttpApiError(status = Status.BadRequest, code = "invalid_actor", message = "invalid_actor")

  import CirceEntityDecoder.*
  import CirceEntityEncoder.*

  def routes(service: FriendRequestService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if SocialRequestTarget.isFriendRequestRespondPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            respond(request, service)
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
      case request if SocialRequestTarget.isFriendRequestPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            list(request, service)
          case Method.POST =>
            create(request, service)
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def list(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    FriendRequestOwnerQuery.parseFromQuery(request.params) match {
      case Left(error) =>
        IO.pure(apiError(ownerApiError(error)))
      case Right(ownerHandle) =>
        blocking(service.list(ownerHandle)).flatMap(records =>
          Ok(FriendRequestListResponse.fromRecords(records).asJson).map(withCors)
        )
    }

  private def create(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    readCreateRequest(request).flatMap {
      case Left(message) =>
        IO.pure(apiError(badRequest(message)))
      case Right(createRequest) =>
        createRequest.toCreateHandles match {
          case Left(error) =>
            IO.pure(apiError(createApiError(error)))
          case Right(command) =>
            blocking(service.create(command.sourceHandle, command.targetHandle)).flatMap {
              case Right(result) =>
                Ok(FriendRequestCreateResponse.fromResult(result).asJson).map(withCors)
              case Left(FriendRequestCreateError.InvalidHandles) =>
                IO.pure(apiError(InvalidHandlesError))
            }
        }
    }

  private def respond(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    readRespondRequest(request).flatMap {
      case Left(message) =>
        IO.pure(apiError(badRequest(message)))
      case Right(respondRequest) =>
        respondRequest.toRespondCommand match {
          case Left(error) =>
            IO.pure(apiError(respondParseApiError(error)))
          case Right(command) =>
            blocking(service.respond(command.requestId, command.actorHandle, command.decision)).flatMap {
              case Right(result) =>
                Ok(FriendRequestRespondResponse.fromResult(result).asJson).map(withCors)
              case Left(FriendRequestRespondError.RequestNotFound) =>
                IO.pure(apiError(RequestNotFoundError))
              case Left(FriendRequestRespondError.Forbidden) =>
                IO.pure(apiError(ForbiddenError))
            }
        }
    }

  private def readCreateRequest(request: Request[IO]): IO[Either[String, FriendRequestCreateApiRequest]] =
    request
      .as[FriendRequestCreateApiRequest]
      .attempt
      .map(_.left.map(_ => "Request body must be a JSON object with string fields."))

  private def readRespondRequest(request: Request[IO]): IO[Either[String, FriendRequestRespondApiRequest]] =
    request
      .as[FriendRequestRespondApiRequest]
      .attempt
      .map(_.left.map(_ => "Request body must be a JSON object with string fields."))

  private def ownerApiError(error: SocialRouteHandleError): HttpApiError =
    error match {
      case SocialRouteHandleError.Missing =>
        MissingOwnerError
      case SocialRouteHandleError.VisitorNotAllowed =>
        VisitorNotAllowedError
      case SocialRouteHandleError.Invalid =>
        InvalidOwnerError
    }

  private def createApiError(error: SocialRouteCreateError): HttpApiError =
    error match {
      case SocialRouteCreateError.InvalidHandles =>
        InvalidHandlesError
      case SocialRouteCreateError.VisitorNotAllowed =>
        VisitorNotAllowedError
    }

  private def respondParseApiError(error: SocialRouteRespondError): HttpApiError =
    error match {
      case SocialRouteRespondError.InvalidDecision =>
        InvalidDecisionError
      case SocialRouteRespondError.MissingFields =>
        MissingFieldsError
      case SocialRouteRespondError.InvalidActorHandle =>
        InvalidActorError
      case SocialRouteRespondError.VisitorNotAllowed =>
        VisitorNotAllowedError
    }

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

  private def badRequest(message: String): HttpApiError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = message)
}
