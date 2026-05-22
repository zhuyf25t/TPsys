package slaydemo.backend.http4s.battle

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

private[http4s] object BattleHttpModule {
  def routes(services: BattleHttpServices): HttpRoutes[IO] =
    BattleQueueHttp4sRoutes.statusRoutes(services.queueService) <+>
      BattleQueueHttp4sRoutes.joinRoutes(services.queueService, services.joinAuthorizationService) <+>
      BattleQueueHttp4sRoutes.leaveRoutes(services.queueService) <+>
      BattleRoomHttp4sRoutes.snapshotRoutes(services.queueService) <+>
      BattleRoomHttp4sRoutes.heartbeatRoutes(services.queueService) <+>
      BattleStateHttp4sRoutes.streamRoutes(services.stateService) <+>
      BattleStateHttp4sRoutes.readRoutes(services.stateService) <+>
      BattleCommandHttp4sRoutes.routes(services.stateService) <+>
      BattleResultHttp4sRoutes.routes(services.resultService)
}
