package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

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
import slaydemo.backend.http4s.Http4sRouteSupport.{blocking, corsNoContent, decodeJsonObjectBody, errorResponse, jsonOk, methodNotAllowedError, requestPath, typedApiError}

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
            corsNoContent
          case Method.GET =>
            BattleQueueRequestTarget.statusTicketIdFrom(request.params) match {
              case None =>
                errorResponse(MissingTicketIdError)
              case Some(ticketId) =>
                blocking(service.status(ticketId)).flatMap {
                  case Right(snapshot) =>
                    jsonOk(BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson)
                  case Left(BattleQueueStatusError.TicketNotFound) =>
                    errorResponse(TicketNotFoundError)
                }
            }
          case _ =>
            errorResponse(StatusMethodNotAllowedError)
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
            corsNoContent
          case Method.POST =>
            decodeJoinRequest(request).flatMap {
              case Left(BattleQueueJoinAPIRequestError.InvalidJsonObject) =>
                errorResponse(InvalidJsonObjectError)
              case Left(BattleQueueJoinAPIRequestError.InvalidRating) =>
                errorResponse(InvalidRatingError)
              case Left(BattleQueueJoinAPIRequestError.InvalidHandle) =>
                errorResponse(InvalidHandleError)
              case Left(BattleQueueJoinAPIRequestError.MissingSession) =>
                errorResponse(MissingSessionError)
              case Right(command) =>
                blocking(joinAuthorizationService.authorize(command)).flatMap {
                  case Left(BattleQueueJoinAuthorizationError.InvalidSession) =>
                    errorResponse(InvalidSessionError)
                  case Left(BattleQueueJoinAuthorizationError.HandleMismatch) =>
                    errorResponse(IdentityMismatchError)
                  case Right(()) =>
                    blocking(queueService.join(command)).flatMap(snapshot =>
                      jsonOk(BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson)
                    )
                }
            }
          case _ =>
            errorResponse(PostMethodNotAllowedError)
        }
    }

  def leaveRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleQueueLeavePath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            decodeLeaveRequest(request).flatMap {
              case Left(BattleQueueLeaveAPIRequestError.InvalidJsonObject) =>
                errorResponse(InvalidJsonObjectError)
              case Left(BattleQueueLeaveAPIRequestError.MissingTicketId) =>
                errorResponse(MissingLeaveTicketIdError)
              case Right(ticketId) =>
                blocking(queueService.leave(ticketId)).flatMap { outcome =>
                  val left = outcome == BattleQueueLeaveOutcome.LeftQueue
                  jsonOk(BattleQueueLeaveAPIResponse(left).asJson)
                }
            }
          case _ =>
            errorResponse(PostMethodNotAllowedError)
        }
    }

  private def isBattleQueueStatusPath(request: Request[IO]): Boolean =
    BattleQueueRequestTarget.isStatusPath(requestPath(request))

  private def isBattleQueueJoinPath(request: Request[IO]): Boolean =
    BattleQueueRequestTarget.isJoinPath(requestPath(request))

  private def isBattleQueueLeavePath(request: Request[IO]): Boolean =
    BattleQueueRequestTarget.isLeavePath(requestPath(request))

  private def decodeJoinRequest(request: Request[IO]): IO[Either[BattleQueueJoinAPIRequestError, BattleQueueJoinCommand]] =
    decodeJsonObjectBody(request, BattleQueueJoinAPIRequestError.InvalidJsonObject)(BattleQueueJoinAPIRequest.decodeCommand)

  private def decodeLeaveRequest(request: Request[IO]): IO[Either[BattleQueueLeaveAPIRequestError, TicketId]] =
    decodeJsonObjectBody(request, BattleQueueLeaveAPIRequestError.InvalidJsonObject)(BattleQueueLeaveAPIRequest.decodeTicketId)
}
