package services.battle.microservices.queue.api.queue

import cats.effect.IO

import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{BattleQueueSnapshot, TicketId}
import services.battle.microservices.queue.services.BattleQueueService
import system.api.APIMessageError

object BattleQueueStatusAPIPlanner {
  def plan(queueService: BattleQueueService, message: BattleQueueStatusAPIMessage): IO[BattleQueueSnapshot] =
    for
      ticketId <- requiredTicketId(message.ticketId)
      result <- queueService.status(ticketId)
      snapshot <- BattleQueueAPIMessageErrors.status(result)
    yield snapshot

  private def requiredTicketId(ticketId: Option[TicketId]): IO[TicketId] =
    IO.fromOption(ticketId)(APIMessageError.BadRequest("ticketId is required."))
}
