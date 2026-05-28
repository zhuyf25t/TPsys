package services.battle.routes

import services.battle.microservices.queue.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService
}
import services.battle.microservices.session.services.BattleStateService

final case class BattleAPIRuntimeContext(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService,
  stateService: BattleStateService
)
