package slaydemo.backend.http4s.battle

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleResultService,
  BattleStateService
}

private[http4s] object BattleHttpModule {
  def routes(
    queueService: BattleQueueService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService,
    resultService: BattleResultService,
    stateService: BattleStateService
  ): HttpRoutes[IO] =
    BattleQueueHttp4sRoutes.statusRoutes(queueService) <+>
      BattleQueueHttp4sRoutes.joinRoutes(queueService, joinAuthorizationService) <+>
      BattleQueueHttp4sRoutes.leaveRoutes(queueService) <+>
      BattleRoomHttp4sRoutes.snapshotRoutes(queueService) <+>
      BattleRoomHttp4sRoutes.heartbeatRoutes(queueService) <+>
      BattleStateHttp4sRoutes.streamRoutes(stateService) <+>
      BattleStateHttp4sRoutes.readRoutes(stateService) <+>
      BattleCommandHttp4sRoutes.routes(stateService) <+>
      BattleResultHttp4sRoutes.routes(resultService)
}
