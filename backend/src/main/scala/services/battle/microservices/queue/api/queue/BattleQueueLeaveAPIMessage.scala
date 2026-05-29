package services.battle.microservices.queue.api.queue

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.queue.api.shared.{BattleQueueRequestDecodeError as BattleQueueLeaveRequestDecodeError}
import services.battle.microservices.queue.objects.queue.{
  BattleQueueLeaveCommand,
  BattleQueueLeaveOutcome
}
import services.battle.microservices.queue.api.queue.BattleQueueLeaveRequest.given
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleQueueLeaveAPIMessage(
  userId: UserId,
  command: BattleQueueLeaveCommand
) extends APIWithTokenContextMessage[BattleQueueService, BattleQueueLeaveOutcome] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[BattleQueueLeaveOutcome] =
    leaveQueue(queueService, command)

  private def leaveQueue(queueService: BattleQueueService, command: BattleQueueLeaveCommand): IO[BattleQueueLeaveOutcome] =
    queueService.leave(command.ticketId)
}

object BattleQueueLeaveAPIMessage {
  given Decoder[BattleQueueLeaveAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequestValue(cursor.value)
          .left
          .map(error => DecodingFailure(BattleQueueLeaveRequestDecodeError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleQueueLeaveAPIMessage(userId, request)
    }

  private def decodeRequestValue(payload: Json): Either[BattleQueueLeaveRequestDecodeError, BattleQueueLeaveCommand] =
    payload.asObject match {
      case None =>
        Left(BattleQueueLeaveRequestDecodeError.InvalidJsonObject)
      case Some(_) =>
        payload.as[BattleQueueLeaveCommand].left.map(_ => BattleQueueLeaveRequestDecodeError.MissingTicketId)
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure if failure.message == "missing_ticket_id" =>
        APIMessageError.BadRequest("ticketId is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle queue leave request.")
    }
}
