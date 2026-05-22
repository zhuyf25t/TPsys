package services.battle.api

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*

import services.battle.objects.RoomId
import services.battle.objects.apiTypes.{
  RealtimeRoomHeartbeatAPIRequest,
  RealtimeRoomHeartbeatAPIRequestError,
  RealtimeRoomSnapshotAPIRequest,
  RealtimeRoomSnapshotAPIRequestError,
  RealtimeRoomSnapshotResponse
}
import services.battle.services.BattleRoomError
import system.api.RegisteredAPIMessage

object BattleRoomSnapshotAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      roomId(payload).flatMap { id =>
        IO.blocking(services.queueService.roomSnapshot(id)).flatMap {
          case Right(snapshot) =>
            BattleAPIMessageSupport.encode(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot))
          case Left(error) =>
            roomError(error)
        }
      }
    }

  private def roomId(payload: Json): IO[RoomId] =
    RealtimeRoomSnapshotAPIRequest.decodeRoomId(payload) match {
      case Right(roomId) =>
        IO.pure(roomId)
      case Left(RealtimeRoomSnapshotAPIRequestError.MissingRoomId) =>
        BattleAPIMessageSupport.badRequest("roomId is required.")
      case Left(RealtimeRoomSnapshotAPIRequestError.InvalidJsonObject) =>
        BattleAPIMessageSupport.badRequest("Invalid battle room snapshot request.")
    }

  private def roomError(error: BattleRoomError): IO[Nothing] =
    error match {
      case BattleRoomError.MissingRoomId =>
        BattleAPIMessageSupport.badRequest("roomId is required.")
      case BattleRoomError.RoomNotFound =>
        BattleAPIMessageSupport.notFound("Battle room was not found.")
    }
}

object BattleRoomHeartbeatAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      RealtimeRoomHeartbeatAPIRequest.decodeCommand(payload, pathRoomId = None, query = Map.empty) match {
        case Left(RealtimeRoomHeartbeatAPIRequestError.InvalidJsonObject) =>
          BattleAPIMessageSupport.badRequest("Invalid battle room heartbeat request.")
        case Right(command) =>
          IO.blocking(services.queueService.heartbeat(command)).flatMap {
            case Right(snapshot) =>
              BattleAPIMessageSupport.encode(RealtimeRoomSnapshotResponse.fromSnapshot(snapshot))
            case Left(BattleRoomError.MissingRoomId) =>
              BattleAPIMessageSupport.badRequest("roomId is required.")
            case Left(BattleRoomError.RoomNotFound) =>
              BattleAPIMessageSupport.notFound("Battle room was not found.")
          }
      }
    }
}
