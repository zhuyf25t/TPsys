package services.battle.microservices.queue.api.shared

import cats.effect.IO
import io.circe.{DecodingFailure, Error}

import services.battle.microservices.queue.objects.queue.{
  BattleQueueSnapshot,
  RealtimeRoomSnapshot
}
import services.battle.microservices.queue.services.{
  BattleQueueJoinAuthorizationError,
  BattleQueueStatusError,
  BattleRoomError
}
import system.api.APIMessageError

private[api] object BattleQueueAPIMessageErrors {
  def joinDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if isLoginRequired(failure) =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if isQueueDecodeError(failure, BattleQueueRequestDecodeError.MissingSession) =>
        APIMessageError.Unauthorized("Session token is required.")
      case failure: DecodingFailure if isQueueDecodeError(failure, BattleQueueRequestDecodeError.InvalidHandle) =>
        APIMessageError.BadRequest("Handle must be a playable non-visitor handle.")
      case failure: DecodingFailure if isQueueDecodeError(failure, BattleQueueRequestDecodeError.InvalidBattleMode) =>
        APIMessageError.BadRequest("Invalid battle mode.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle queue join request.")
    }

  def statusDecodeFailure(error: Error): APIMessageError =
    ticketScopedDecodeFailure(
      error = error,
      invalidRequestMessage = "Invalid battle queue status request."
    )

  def leaveDecodeFailure(error: Error): APIMessageError =
    ticketScopedDecodeFailure(
      error = error,
      invalidRequestMessage = "Invalid battle queue leave request."
    )

  def roomSnapshotDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if isLoginRequired(failure) =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if isQueueDecodeError(failure, BattleQueueRequestDecodeError.MissingRoomId) =>
        APIMessageError.BadRequest("roomId is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle room snapshot request.")
    }

  def roomHeartbeatDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if isLoginRequired(failure) =>
        APIMessageError.Unauthorized("Login is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle room heartbeat request.")
    }

  def joinAuthorization(result: Either[BattleQueueJoinAuthorizationError, Unit]): IO[Unit] =
    result.fold(raiseJoinAuthorization, _ => IO.unit)

  def status(result: Either[BattleQueueStatusError, BattleQueueSnapshot]): IO[BattleQueueSnapshot] =
    result.fold(raiseStatus, IO.pure)

  def room(result: Either[BattleRoomError, RealtimeRoomSnapshot]): IO[RealtimeRoomSnapshot] =
    result.fold(raiseRoom, IO.pure)

  private def ticketScopedDecodeFailure(error: Error, invalidRequestMessage: String): APIMessageError =
    error match {
      case failure: DecodingFailure if isLoginRequired(failure) =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if isQueueDecodeError(failure, BattleQueueRequestDecodeError.MissingTicketId) =>
        APIMessageError.BadRequest("ticketId is required.")
      case _ =>
        APIMessageError.BadRequest(invalidRequestMessage)
    }

  private def isLoginRequired(failure: DecodingFailure): Boolean =
    failure.message == "Login is required."

  private def isQueueDecodeError(failure: DecodingFailure, error: BattleQueueRequestDecodeError): Boolean =
    failure.message == BattleQueueRequestDecodeError.message(error)

  private def raiseJoinAuthorization(error: BattleQueueJoinAuthorizationError): IO[Nothing] =
    error match {
      case BattleQueueJoinAuthorizationError.InvalidSession =>
        IO.raiseError(APIMessageError.Unauthorized("Session token is not valid."))
      case BattleQueueJoinAuthorizationError.HandleMismatch =>
        IO.raiseError(APIMessageError.Forbidden("Session does not belong to the requested handle."))
    }

  private def raiseStatus(error: BattleQueueStatusError): IO[Nothing] =
    error match {
      case BattleQueueStatusError.TicketNotFound =>
        IO.raiseError(APIMessageError.NotFound("Queue ticket was not found."))
    }

  private def raiseRoom(error: BattleRoomError): IO[Nothing] =
    error match {
      case BattleRoomError.MissingRoomId =>
        IO.raiseError(APIMessageError.BadRequest("roomId is required."))
      case BattleRoomError.RoomNotFound =>
        IO.raiseError(APIMessageError.NotFound("Battle room was not found."))
    }
}
