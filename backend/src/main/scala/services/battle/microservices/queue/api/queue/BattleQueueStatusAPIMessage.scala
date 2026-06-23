package services.battle.microservices.queue.api.queue

import cats.effect.IO
import io.circe.{Decoder, Error}
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{
  BattleQueueSnapshot,
  TicketId
}
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleQueueStatusAPIMessage(
  userId: UserId,
  ticketId: Option[TicketId]
) extends APIWithTokenContextMessage[BattleQueueService, BattleQueueSnapshot] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[BattleQueueSnapshot] =
    BattleQueueStatusAPIPlanner.plan(queueService, this)
}

object BattleQueueStatusAPIMessage {
  import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageDecoding.given

  given Decoder[BattleQueueStatusAPIMessage] =
    deriveDecoder[BattleQueueStatusAPIMessage]

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleQueueAPIMessageErrors.statusDecodeFailure(error)
}
