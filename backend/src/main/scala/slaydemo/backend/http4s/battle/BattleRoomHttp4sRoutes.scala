package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request, Status}

import slaydemo.backend.battle.objects.apiTypes.{
  RealtimeRoomHeartbeatAPIRequest,
  RealtimeRoomHeartbeatAPIRequestError,
  RealtimeRoomRequestTarget,
  RealtimeRoomSnapshotResponse
}
import slaydemo.backend.battle.services.{BattleQueueService, BattleRoomError, RealtimeRoomHeartbeatCommand}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, decodeJsonObjectBody, errorResponse, jsonOk, requestPath}

private[http4s] object BattleRoomHttp4sRoutes {
  private val InvalidRoomIdError =
    apiError(
      Status.BadRequest,
      "invalid_room_id",
      "roomId is required."
    )
  private val InvalidJsonObjectError =
    apiError(
      Status.BadRequest,
      "bad_request",
      "Request body must be a JSON object with supported primitive or object fields."
    )
  private val RoomNotFoundError =
    apiError(
      Status.NotFound,
      "room_not_found",
      "Battle room was not found."
    )
  private val SnapshotMethodNotAllowedError =
    apiError(
      Status.MethodNotAllowed,
      "method_not_allowed",
      "Only GET and OPTIONS are supported."
    )
  private val HeartbeatMethodNotAllowedError =
    apiError(
      Status.MethodNotAllowed,
      "method_not_allowed",
      "Only POST and OPTIONS are supported."
    )

  def snapshotRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleRoomSnapshotPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            roomIdFromSnapshotRequest(request) match {
              case None =>
                errorResponse(InvalidRoomIdError)
              case Some(roomId) =>
                blocking(queueService.roomSnapshot(roomId)).flatMap {
                  case Right(snapshot) =>
                    jsonOk(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson)
                  case Left(error) =>
                    errorResponse(roomApiError(error))
                }
            }
          case _ =>
            errorResponse(SnapshotMethodNotAllowedError)
        }
    }

  def heartbeatRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleRoomHeartbeatPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            decodeHeartbeatRequest(request).flatMap {
              case Left(RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject) =>
                errorResponse(InvalidJsonObjectError)
              case Right(command) =>
                blocking(queueService.heartbeat(command)).flatMap {
                  case Right(snapshot) =>
                    jsonOk(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson)
                  case Left(error) =>
                    errorResponse(roomApiError(error))
                }
            }
          case _ =>
            errorResponse(HeartbeatMethodNotAllowedError)
        }
    }

  private def isBattleRoomSnapshotPath(request: Request[IO]): Boolean =
    RealtimeRoomRequestTarget.isSnapshotPath(requestPath(request))

  private def isBattleRoomHeartbeatPath(request: Request[IO]): Boolean =
    RealtimeRoomRequestTarget.isHeartbeatPath(requestPath(request))

  private def decodeHeartbeatRequest(request: Request[IO]): IO[Either[RealtimeRoomHeartbeatAPIRequestError, RealtimeRoomHeartbeatCommand]] =
    decodeJsonObjectBody(request, RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject) { json =>
      RealtimeRoomHeartbeatAPIRequest.decodeCommand(
        json,
        RealtimeRoomRequestTarget.roomIdFromHeartbeatPath(requestPath(request)),
        request.params
      )
    }

  private def roomIdFromSnapshotRequest(request: Request[IO]) =
    RealtimeRoomRequestTarget.roomIdFromSnapshot(requestPath(request), request.params)

  private def roomApiError(error: BattleRoomError): HttpApiError =
    error match {
      case BattleRoomError.MissingRoomId => InvalidRoomIdError
      case BattleRoomError.RoomNotFound  => RoomNotFoundError
    }
}
