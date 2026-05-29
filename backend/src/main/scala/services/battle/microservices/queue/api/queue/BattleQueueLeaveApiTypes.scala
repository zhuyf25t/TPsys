package services.battle.microservices.queue.api.queue

import io.circe.{Decoder, DecodingFailure, Encoder}

import services.battle.microservices.queue.objects.queue.{BattleQueueLeaveCommand, BattleQueueLeaveOutcome, TicketId}

object BattleQueueLeaveRequest {
  given Decoder[BattleQueueLeaveCommand] =
    Decoder.instance { cursor =>
      cursor.get[String]("ticketId").flatMap { value =>
        Option(value).map(_.trim).filter(_.nonEmpty) match {
          case Some(ticketId) =>
            Right(BattleQueueLeaveCommand(TicketId(ticketId)))
          case None =>
            Left(DecodingFailure("ticketId is required.", cursor.history))
        }
      }
    }
}

object BattleQueueLeaveResponse {
  given Encoder[BattleQueueLeaveOutcome] =
    Encoder.forProduct1("left")(isLeft)

  private def isLeft(outcome: BattleQueueLeaveOutcome): Boolean =
    outcome match {
      case BattleQueueLeaveOutcome.LeftQueue =>
        true
      case BattleQueueLeaveOutcome.NotWaiting | BattleQueueLeaveOutcome.TicketNotFound =>
        false
    }
}
