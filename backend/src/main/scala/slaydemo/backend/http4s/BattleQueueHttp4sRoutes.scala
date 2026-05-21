package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.battle.objects.TicketId
import slaydemo.backend.battle.objects.apiTypes.{
  BattleQueueJoinAPIRequest,
  BattleQueueJoinAPIRequestError,
  BattleQueueLeaveAPIRequest,
  BattleQueueLeaveAPIRequestError,
  BattleQueueLeaveAPIResponse,
  BattleQueueRequestTarget,
  BattleQueueSnapshotResponse
}
import slaydemo.backend.battle.services.{
  BattleQueueJoinCommand,
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueLeaveOutcome,
  BattleQueueService,
  BattleQueueStatusError
}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, methodNotAllowedError, typedApiError, withCors}

private[http4s] object BattleQueueHttp4sRoutes {
  private val InvalidJsonObjectError =
    typedApiError(
      statusCode = 400,
      code = "bad_request",
      message = "Request body must be a JSON object with supported primitive or object fields."
    )
  private val MissingTicketIdError =
    typedApiError(statusCode = 400, code = "missing_ticket_id", message = "ticketId query parameter is required.")
  private val MissingLeaveTicketIdError =
    typedApiError(statusCode = 400, code = "bad_request", message = "ticketId is required.")
  private val TicketNotFoundError =
    typedApiError(statusCode = 404, code = "ticket_not_found", message = "Queue ticket was not found.")
  private val InvalidHandleError =
    typedApiError(statusCode = 400, code = "invalid_handle", message = "Handle must be a playable non-visitor handle.")
  private val InvalidRatingError =
    typedApiError(statusCode = 400, code = "bad_request", message = "rating must be an integer.")
  private val MissingSessionError =
    typedApiError(statusCode = 401, code = "missing_session", message = "Session token is required.")
  private val InvalidSessionError =
    typedApiError(statusCode = 401, code = "invalid_session", message = "Session token is not valid.")
  private val IdentityMismatchError =
    typedApiError(statusCode = 403, code = "identity_mismatch", message = "Session does not belong to the requested handle.")
  private val StatusMethodNotAllowedError =
    methodNotAllowedError("Only GET and OPTIONS are supported.")
  private val PostMethodNotAllowedError =
    methodNotAllowedError("Only POST and OPTIONS are supported.")

  def statusRoutes(service: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleQueueStatusPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            BattleQueueRequestTarget.statusTicketIdFrom(request.params) match {
              case None =>
                IO.pure(apiError(MissingTicketIdError))
              case Some(ticketId) =>
                blocking(service.status(ticketId)).flatMap {
                  case Right(snapshot) =>
                    Ok(BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson).map(withCors)
                  case Left(BattleQueueStatusError.TicketNotFound) =>
                    IO.pure(apiError(TicketNotFoundError))
                }
            }
          case _ =>
            IO.pure(apiError(StatusMethodNotAllowedError))
        }
    }

  def joinRoutes(
    queueService: BattleQueueService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleQueueJoinPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            decodeJoinRequest(request).flatMap {
              case Left(BattleQueueJoinAPIRequestError.InvalidJsonObject) =>
                IO.pure(apiError(InvalidJsonObjectError))
              case Left(BattleQueueJoinAPIRequestError.InvalidRating) =>
                IO.pure(apiError(InvalidRatingError))
              case Left(BattleQueueJoinAPIRequestError.InvalidHandle) =>
                IO.pure(apiError(InvalidHandleError))
              case Left(BattleQueueJoinAPIRequestError.MissingSession) =>
                IO.pure(apiError(MissingSessionError))
              case Right(command) =>
                blocking(joinAuthorizationService.authorize(command)).flatMap {
                  case Left(BattleQueueJoinAuthorizationError.InvalidSession) =>
                    IO.pure(apiError(InvalidSessionError))
                  case Left(BattleQueueJoinAuthorizationError.HandleMismatch) =>
                    IO.pure(apiError(IdentityMismatchError))
                  case Right(()) =>
                    blocking(queueService.join(command)).flatMap(snapshot =>
                      Ok(BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson).map(withCors)
                    )
                }
            }
          case _ =>
            IO.pure(apiError(PostMethodNotAllowedError))
        }
    }

  def leaveRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleQueueLeavePath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            decodeLeaveRequest(request).flatMap {
              case Left(BattleQueueLeaveAPIRequestError.InvalidJsonObject) =>
                IO.pure(apiError(InvalidJsonObjectError))
              case Left(BattleQueueLeaveAPIRequestError.MissingTicketId) =>
                IO.pure(apiError(MissingLeaveTicketIdError))
              case Right(ticketId) =>
                blocking(queueService.leave(ticketId)).flatMap { outcome =>
                  val left = outcome == BattleQueueLeaveOutcome.LeftQueue
                  Ok(BattleQueueLeaveAPIResponse(left).asJson).map(withCors)
                }
            }
          case _ =>
            IO.pure(apiError(PostMethodNotAllowedError))
        }
    }

  private def isBattleQueueStatusPath(request: Request[IO]): Boolean =
    BattleQueueRequestTarget.isStatusPath(request.uri.path.renderString)

  private def isBattleQueueJoinPath(request: Request[IO]): Boolean =
    BattleQueueRequestTarget.isJoinPath(request.uri.path.renderString)

  private def isBattleQueueLeavePath(request: Request[IO]): Boolean =
    BattleQueueRequestTarget.isLeavePath(request.uri.path.renderString)

  private def decodeJoinRequest(request: Request[IO]): IO[Either[BattleQueueJoinAPIRequestError, BattleQueueJoinCommand]] =
    request.as[Json].attempt.map {
      case Left(_) =>
        Left(BattleQueueJoinAPIRequestError.InvalidJsonObject)
      case Right(json) if json.asObject.isEmpty =>
        Left(BattleQueueJoinAPIRequestError.InvalidJsonObject)
      case Right(json) =>
        BattleQueueJoinAPIRequest.decodeCommand(json)
    }

  private def decodeLeaveRequest(request: Request[IO]): IO[Either[BattleQueueLeaveAPIRequestError, TicketId]] =
    request.as[Json].attempt.map {
      case Left(_) =>
        Left(BattleQueueLeaveAPIRequestError.InvalidJsonObject)
      case Right(json) if json.asObject.isEmpty =>
        Left(BattleQueueLeaveAPIRequestError.InvalidJsonObject)
      case Right(json) =>
        BattleQueueLeaveAPIRequest.decodeTicketId(json)
    }
}
