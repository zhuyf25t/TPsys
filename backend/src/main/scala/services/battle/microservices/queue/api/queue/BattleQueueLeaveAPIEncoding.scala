package services.battle.microservices.queue.api.queue

import io.circe.Encoder
import services.battle.microservices.queue.objects.queue.BattleQueueLeaveOutcome

object BattleQueueLeaveAPIEncoding {
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
