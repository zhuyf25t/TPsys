package services.battle.routes

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*

import services.battle.objects.TicketId
import services.battle.objects.apiTypes.{
  BattleQueueJoinAPIRequest,
  BattleQueueJoinAPIRequestError,
  BattleQueueLeaveAPIRequest,
  BattleQueueLeaveAPIRequestError,
  BattleQueueLeaveAPIResponse,
  BattleQueueStatusAPIRequest,
  BattleQueueStatusAPIRequestError,
  BattleQueueSnapshotResponse
}
import services.battle.services.{
  BattleQueueJoinAuthorizationError,
  BattleQueueStatusError
}
import system.api.RegisteredAPIMessage

object BattleQueueJoinAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      for
        command <- BattleQueueJoinAPIRequest.decodeCommand(payload) match {
          case Right(command) => IO.pure(command)
          case Left(error)    => joinRequestError(error)
        }
        _ <- IO.blocking(services.joinAuthorizationService.authorize(command)).flatMap {
          case Right(()) => IO.unit
          case Left(error) => joinAuthorizationError(error)
        }
        snapshot <- IO.blocking(services.queueService.join(command))
      yield BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson
    }

  private def joinRequestError(error: BattleQueueJoinAPIRequestError): IO[Nothing] =
    error match {
      case BattleQueueJoinAPIRequestError.InvalidHandle =>
        BattleAPIMessageSupport.badRequest("Handle must be a playable non-visitor handle.")
      case BattleQueueJoinAPIRequestError.MissingSession =>
        BattleAPIMessageSupport.unauthorized("Session token is required.")
      case BattleQueueJoinAPIRequestError.InvalidRating | BattleQueueJoinAPIRequestError.InvalidJsonObject =>
        BattleAPIMessageSupport.badRequest("Invalid battle queue join request.")
    }

  private def joinAuthorizationError(error: BattleQueueJoinAuthorizationError): IO[Nothing] =
    error match {
      case BattleQueueJoinAuthorizationError.InvalidSession =>
        BattleAPIMessageSupport.unauthorized("Session token is not valid.")
      case BattleQueueJoinAuthorizationError.HandleMismatch =>
        BattleAPIMessageSupport.forbidden("Session does not belong to the requested handle.")
    }
}

object BattleQueueStatusAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      ticketId(payload).flatMap { id =>
        IO.blocking(services.queueService.status(id)).flatMap {
          case Right(snapshot) =>
            BattleAPIMessageSupport.encode(BattleQueueSnapshotResponse.fromSnapshot(snapshot))
          case Left(BattleQueueStatusError.TicketNotFound) =>
            BattleAPIMessageSupport.notFound("Queue ticket was not found.")
        }
      }
    }

  private def ticketId(payload: Json): IO[TicketId] =
    BattleQueueStatusAPIRequest.decodeTicketId(payload) match {
      case Right(ticketId) =>
        IO.pure(ticketId)
      case Left(BattleQueueStatusAPIRequestError.MissingTicketId) =>
        BattleAPIMessageSupport.badRequest("ticketId is required.")
      case Left(BattleQueueStatusAPIRequestError.InvalidJsonObject) =>
        BattleAPIMessageSupport.badRequest("Invalid battle queue status request.")
    }
}

object BattleQueueLeaveAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      BattleQueueLeaveAPIRequest.decodeTicketId(payload) match {
        case Left(BattleQueueLeaveAPIRequestError.MissingTicketId) =>
          BattleAPIMessageSupport.badRequest("ticketId is required.")
        case Left(BattleQueueLeaveAPIRequestError.InvalidJsonObject) =>
          BattleAPIMessageSupport.badRequest("Invalid battle queue leave request.")
        case Right(ticketId) =>
          IO.blocking(services.queueService.leave(ticketId)).flatMap(outcome =>
            BattleAPIMessageSupport.encode(BattleQueueLeaveAPIResponse.fromOutcome(outcome))
          )
      }
    }
}
