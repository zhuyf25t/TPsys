package services.battle.microservices.queue.api.queue

import cats.effect.IO
import io.circe.{Decoder, Error}
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{
  BattleQueueLeaveOutcome,
  TicketId
}
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleQueueLeaveAPIMessage(
  userId: UserId,
  ticketId: Option[TicketId]
) extends APIWithTokenContextMessage[BattleQueueService, BattleQueueLeaveOutcome] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[BattleQueueLeaveOutcome] =
    BattleQueueLeaveAPIPlanner.plan(queueService, this)
}

object BattleQueueLeaveAPIMessage {
  import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageDecoding.given

  given Decoder[BattleQueueLeaveAPIMessage] =
    deriveDecoder[BattleQueueLeaveAPIMessage]

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleQueueAPIMessageErrors.leaveDecodeFailure(error)
}
