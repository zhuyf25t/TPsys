package services.battle.api

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*

import services.battle.objects.RoomId
import services.battle.objects.apiTypes.{
  RealtimeRoomHeartbeatAPIRequest,
  RealtimeRoomHeartbeatAPIRequestError,
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
    payload.hcursor.get[Option[String]]("roomId") match {
      case Right(Some(value)) if value.trim.nonEmpty =>
        IO.pure(RoomId(value.trim))
      case _ =>
        BattleAPIMessageSupport.badRequest("roomId is required.")
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
