package services.battle.microservices.queue.api.queue

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.{BattleQueueLeaveOutcome, TicketId}
import services.battle.microservices.queue.services.BattleQueueService
import system.api.APIMessageError

object BattleQueueLeaveAPIPlanner {
  def plan(queueService: BattleQueueService, message: BattleQueueLeaveAPIMessage): IO[BattleQueueLeaveOutcome] =
    for
      ticketId <- requiredTicketId(message.ticketId)
      outcome <- queueService.leave(ticketId)
    yield outcome

  private def requiredTicketId(ticketId: Option[TicketId]): IO[TicketId] =
    IO.fromOption(ticketId)(APIMessageError.BadRequest("ticketId is required."))
}
