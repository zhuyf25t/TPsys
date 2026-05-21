package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.battle.objects.apiTypes.{
  RealtimeRoomHeartbeatAPIRequest,
  RealtimeRoomHeartbeatAPIRequestError,
  RealtimeRoomRequestTarget,
  RealtimeRoomSnapshotResponse
}
import slaydemo.backend.battle.services.{BattleQueueService, BattleRoomError, RealtimeRoomHeartbeatCommand}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, methodNotAllowedError, typedApiError, withCors}

private[http4s] object BattleRoomHttp4sRoutes {
  private val InvalidRoomIdError =
    typedApiError(statusCode = 400, code = "invalid_room_id", message = "roomId is required.")
  private val InvalidJsonObjectError =
    typedApiError(
      statusCode = 400,
      code = "bad_request",
      message = "Request body must be a JSON object with supported primitive or object fields."
    )
  private val RoomNotFoundError =
    typedApiError(statusCode = 404, code = "room_not_found", message = "Battle room was not found.")
  private val SnapshotMethodNotAllowedError =
    methodNotAllowedError("Only GET and OPTIONS are supported.")
  private val HeartbeatMethodNotAllowedError =
    methodNotAllowedError("Only POST and OPTIONS are supported.")

  def snapshotRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleRoomSnapshotPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            roomIdFromSnapshotRequest(request) match {
              case None =>
                IO.pure(apiError(InvalidRoomIdError))
              case Some(roomId) =>
                blocking(queueService.roomSnapshot(roomId)).flatMap {
                  case Right(snapshot) =>
                    Ok(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson).map(withCors)
                  case Left(error) =>
                    IO.pure(apiError(roomApiError(error)))
                }
            }
          case _ =>
            IO.pure(apiError(SnapshotMethodNotAllowedError))
        }
    }

  def heartbeatRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleRoomHeartbeatPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            decodeHeartbeatRequest(request).flatMap {
              case Left(RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject) =>
                IO.pure(apiError(InvalidJsonObjectError))
              case Right(command) =>
                blocking(queueService.heartbeat(command)).flatMap {
                  case Right(snapshot) =>
                    Ok(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson).map(withCors)
                  case Left(error) =>
                    IO.pure(apiError(roomApiError(error)))
                }
            }
          case _ =>
            IO.pure(apiError(HeartbeatMethodNotAllowedError))
        }
    }

  private def isBattleRoomSnapshotPath(request: Request[IO]): Boolean =
    RealtimeRoomRequestTarget.isSnapshotPath(request.uri.path.renderString)

  private def isBattleRoomHeartbeatPath(request: Request[IO]): Boolean =
    RealtimeRoomRequestTarget.isHeartbeatPath(request.uri.path.renderString)

  private def decodeHeartbeatRequest(request: Request[IO]): IO[Either[RealtimeRoomHeartbeatAPIRequestError, RealtimeRoomHeartbeatCommand]] =
    request.as[Json].attempt.map {
      case Left(_) =>
        Left(RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject)
      case Right(json) if json.asObject.isEmpty =>
        Left(RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject)
      case Right(json) =>
        RealtimeRoomHeartbeatAPIRequest.decodeCommand(
          json,
          RealtimeRoomRequestTarget.roomIdFromHeartbeatPath(request.uri.path.renderString),
          request.params
        )
    }

  private def roomIdFromSnapshotRequest(request: Request[IO]) =
    RealtimeRoomRequestTarget.roomIdFromSnapshot(request.uri.path.renderString, request.params)

  private def roomApiError(error: BattleRoomError): HttpApiError =
    error match {
      case BattleRoomError.MissingRoomId => InvalidRoomIdError
      case BattleRoomError.RoomNotFound  => RoomNotFoundError
    }
}
