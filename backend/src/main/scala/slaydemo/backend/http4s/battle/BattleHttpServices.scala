package slaydemo.backend.http4s.battle

import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleResultService,
  BattleStateService
}

private[http4s] final case class BattleHttpServices(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService,
  resultService: BattleResultService,
  stateService: BattleStateService
)
