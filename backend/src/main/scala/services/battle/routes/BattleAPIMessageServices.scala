package services.battle.routes

import services.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleResultService,
  BattleStateService
}

final case class BattleAPIMessageServices(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService,
  resultService: BattleResultService,
  stateService: BattleStateService
)
