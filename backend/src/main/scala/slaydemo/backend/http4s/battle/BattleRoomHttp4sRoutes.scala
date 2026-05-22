package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import slaydemo.backend.battle.objects.apiTypes.{
  BattleRoomApiErrorCode,
  RealtimeRoomHeartbeatAPIRequest,
  RealtimeRoomHeartbeatAPIRequestError,
  RealtimeRoomRequestTarget,
  RealtimeRoomSnapshotResponse
}
import slaydemo.backend.battle.services.{BattleQueueService, BattleRoomError, RealtimeRoomHeartbeatCommand}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.HttpApiErrors.typedApiError
import slaydemo.backend.http4s.Http4sCors.corsNoContent
import slaydemo.backend.http4s.Http4sEffects.blocking
import slaydemo.backend.http4s.Http4sRequestDecoders.decodeJsonObjectBody
import slaydemo.backend.http4s.Http4sRequestPaths.requestPath
import slaydemo.backend.http4s.Http4sResponses.{errorResponse, jsonOk}

private[http4s] object BattleRoomHttp4sRoutes {
  def snapshotRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleRoomSnapshotPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            roomIdFromSnapshotRequest(request) match {
              case None =>
                errorResponse(battleRoomApiError(BattleRoomApiErrorCode.InvalidRoomId))
              case Some(roomId) =>
                blocking(queueService.roomSnapshot(roomId)).flatMap {
                  case Right(snapshot) =>
                    jsonOk(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson)
                  case Left(error) =>
                    errorResponse(roomApiError(error))
                }
            }
          case _ =>
            errorResponse(battleRoomApiError(BattleRoomApiErrorCode.SnapshotMethodNotAllowed))
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
              case Left(error) =>
                errorResponse(heartbeatRequestApiError(error))
              case Right(command) =>
                blocking(queueService.heartbeat(command)).flatMap {
                  case Right(snapshot) =>
                    jsonOk(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson)
                  case Left(error) =>
                    errorResponse(roomApiError(error))
                }
            }
          case _ =>
            errorResponse(battleRoomApiError(BattleRoomApiErrorCode.HeartbeatMethodNotAllowed))
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
    battleRoomApiError(BattleRoomApiErrorCode.fromRoomError(error))

  private def heartbeatRequestApiError(error: RealtimeRoomHeartbeatAPIRequestError): HttpApiError =
    battleRoomApiError(BattleRoomApiErrorCode.fromHeartbeatRequestError(error))

  private def battleRoomApiError(code: BattleRoomApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = BattleRoomApiErrorCode.statusCode(code),
      code = BattleRoomApiErrorCode.wireValue(code),
      message = BattleRoomApiErrorCode.message(code)
    )
}
