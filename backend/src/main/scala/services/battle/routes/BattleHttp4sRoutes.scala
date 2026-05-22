package services.battle.routes

import cats.effect.IO
import org.http4s.HttpRoutes
import system.api.APIMessageRouter

object BattleHttp4sRoutes {
  def routes(services: BattleAPIMessageServices): HttpRoutes[IO] =
    APIMessageRouter.routes(BattleAPIMessageRegistry.registered(services))
}
