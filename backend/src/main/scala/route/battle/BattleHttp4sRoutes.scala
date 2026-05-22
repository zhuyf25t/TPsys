package route.battle

import cats.effect.IO
import org.http4s.HttpRoutes
import services.battle.api.{BattleAPIMessageRegistry, BattleAPIMessageServices}
import system.api.APIMessageRouter

private[route] object BattleHttp4sRoutes {
  def routes(services: BattleAPIMessageServices): HttpRoutes[IO] =
    APIMessageRouter.routes(BattleAPIMessageRegistry.registered(services))
}
