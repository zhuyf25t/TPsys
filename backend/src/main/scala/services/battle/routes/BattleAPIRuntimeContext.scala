package services.battle.routes

import services.battle.database.queue.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService
}
import services.battle.database.session.BattleStateService

final case class BattleAPIRuntimeContext(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService,
  stateService: BattleStateService
)
