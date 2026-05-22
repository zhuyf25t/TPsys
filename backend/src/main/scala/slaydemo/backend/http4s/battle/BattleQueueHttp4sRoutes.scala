package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import slaydemo.backend.battle.objects.TicketId
import slaydemo.backend.battle.objects.apiTypes.{
  BattleQueueApiErrorCode,
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
  BattleQueueService,
  BattleQueueStatusError
}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.HttpApiErrors.typedApiError
import slaydemo.backend.http4s.Http4sCors.corsNoContent
import slaydemo.backend.http4s.Http4sEffects.blocking
import slaydemo.backend.http4s.Http4sRequestDecoders.decodeJsonObjectBody
import slaydemo.backend.http4s.Http4sRequestPaths.requestPath
import slaydemo.backend.http4s.Http4sResponses.{errorResponse, jsonOk}

private[http4s] object BattleQueueHttp4sRoutes {
  def statusRoutes(service: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleQueueStatusPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            BattleQueueRequestTarget.statusTicketIdFrom(request.params) match {
              case None =>
                errorResponse(battleQueueApiError(BattleQueueApiErrorCode.MissingStatusTicketId))
              case Some(ticketId) =>
                blocking(service.status(ticketId)).flatMap {
                  case Right(snapshot) =>
                    jsonOk(BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson)
                  case Left(error) =>
                    errorResponse(statusApiError(error))
                }
            }
          case _ =>
            errorResponse(battleQueueApiError(BattleQueueApiErrorCode.StatusMethodNotAllowed))
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
              case Left(error) =>
                errorResponse(joinRequestApiError(error))
              case Right(command) =>
                blocking(joinAuthorizationService.authorize(command)).flatMap {
                  case Left(error) =>
                    errorResponse(joinAuthorizationApiError(error))
                  case Right(()) =>
                    blocking(queueService.join(command)).flatMap(snapshot =>
                      jsonOk(BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson)
                    )
                }
            }
          case _ =>
            errorResponse(battleQueueApiError(BattleQueueApiErrorCode.PostMethodNotAllowed))
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
              case Left(error) =>
                errorResponse(leaveRequestApiError(error))
              case Right(ticketId) =>
                blocking(queueService.leave(ticketId)).flatMap(outcome =>
                  jsonOk(BattleQueueLeaveAPIResponse.fromOutcome(outcome).asJson)
                )
            }
          case _ =>
            errorResponse(battleQueueApiError(BattleQueueApiErrorCode.PostMethodNotAllowed))
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

  private def statusApiError(error: BattleQueueStatusError): HttpApiError =
    battleQueueApiError(BattleQueueApiErrorCode.fromStatusError(error))

  private def joinRequestApiError(error: BattleQueueJoinAPIRequestError): HttpApiError =
    battleQueueApiError(BattleQueueApiErrorCode.fromJoinRequestError(error))

  private def joinAuthorizationApiError(error: BattleQueueJoinAuthorizationError): HttpApiError =
    battleQueueApiError(BattleQueueApiErrorCode.fromJoinAuthorizationError(error))

  private def leaveRequestApiError(error: BattleQueueLeaveAPIRequestError): HttpApiError =
    battleQueueApiError(BattleQueueApiErrorCode.fromLeaveRequestError(error))

  private def battleQueueApiError(code: BattleQueueApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = BattleQueueApiErrorCode.statusCode(code),
      code = BattleQueueApiErrorCode.wireValue(code),
      message = BattleQueueApiErrorCode.message(code)
    )
}
