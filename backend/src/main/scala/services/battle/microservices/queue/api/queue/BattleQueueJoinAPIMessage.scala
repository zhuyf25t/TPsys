package services.battle.microservices.queue.api.queue

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.microservices.queue.services.{
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueService
}
import services.battle.microservices.queue.api.shared.{BattleQueueRequestDecodeError as BattleQueueJoinRequestDecodeError}
import services.battle.microservices.queue.objects.queue.{
  BattleQueueJoinCommand,
  BattleQueueSnapshot
}
import services.battle.microservices.queue.api.queue.BattleQueueJoinRequest.given
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleQueueJoinAPIContext(
  queueService: BattleQueueService,
  authorizationService: BattleQueueJoinAuthorizationService
)

final case class BattleQueueJoinAPIMessage(
  userId: UserId,
  command: BattleQueueJoinCommand
) extends APIWithTokenContextMessage[BattleQueueJoinAPIContext, BattleQueueSnapshot] {
  override def plan(context: BattleQueueJoinAPIContext, connection: Connection): IO[BattleQueueSnapshot] =
    for
      _ <- authorizeJoin(context.authorizationService, command)
      snapshot <- joinQueue(context.queueService, command)
    yield snapshot

  private def authorizeJoin(
    authorizationService: BattleQueueJoinAuthorizationService,
    command: BattleQueueJoinCommand
  ): IO[Unit] =
    for
      result <- authorizationService.authorize(command)
      authorized <- authorizationResult(result)
    yield authorized

  private def authorizationResult(result: Either[BattleQueueJoinAuthorizationError, Unit]): IO[Unit] =
    result match {
      case Right(()) =>
        IO.unit
      case Left(error) =>
        authorizationError(error)
    }

  private def authorizationError(error: BattleQueueJoinAuthorizationError): IO[Nothing] =
    error match {
      case BattleQueueJoinAuthorizationError.InvalidSession =>
        IO.raiseError(APIMessageError.Unauthorized("Session token is not valid."))
      case BattleQueueJoinAuthorizationError.HandleMismatch =>
        IO.raiseError(APIMessageError.Forbidden("Session does not belong to the requested handle."))
    }

  private def joinQueue(queueService: BattleQueueService, command: BattleQueueJoinCommand): IO[BattleQueueSnapshot] =
    queueService.join(command)
}

object BattleQueueJoinAPIMessage {
  given Decoder[BattleQueueJoinAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequestValue(cursor.value)
          .left
          .map(error => DecodingFailure(BattleQueueJoinRequestDecodeError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleQueueJoinAPIMessage(userId, request)
    }

  private def decodeRequestValue(payload: Json): Either[BattleQueueJoinRequestDecodeError, BattleQueueJoinCommand] =
    payload.asObject match {
      case None =>
        Left(BattleQueueJoinRequestDecodeError.InvalidJsonObject)
      case Some(_) =>
        payload.as[BattleQueueJoinCommand].left.map(queueJoinRequestDecodeError)
    }

  private def queueJoinRequestDecodeError(error: io.circe.Error): BattleQueueJoinRequestDecodeError =
    error match {
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.InvalidBattleMode) =>
        BattleQueueJoinRequestDecodeError.InvalidBattleMode
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.InvalidRating) =>
        BattleQueueJoinRequestDecodeError.InvalidRating
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.InvalidHandle) =>
        BattleQueueJoinRequestDecodeError.InvalidHandle
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.MissingSession) =>
        BattleQueueJoinRequestDecodeError.MissingSession
      case _ =>
        BattleQueueJoinRequestDecodeError.InvalidJsonObject
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.MissingSession) =>
        APIMessageError.Unauthorized("Session token is required.")
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.InvalidHandle) =>
        APIMessageError.BadRequest("Handle must be a playable non-visitor handle.")
      case failure: DecodingFailure if failure.message == BattleQueueJoinRequestDecodeError.message(BattleQueueJoinRequestDecodeError.InvalidBattleMode) =>
        APIMessageError.BadRequest("Invalid battle mode.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle queue join request.")
    }
}
