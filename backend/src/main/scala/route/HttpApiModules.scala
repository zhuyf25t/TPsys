package route

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2

import route.battle.BattleHttp4sRoutes
import route.bots.BotProfileHttpModule
import route.forum.ForumHttpModule
import route.governance.GovernanceHttpModule
import route.health.HealthHttpModule
import route.identity.IdentityHttpModule
import route.mail.MailHttpModule
import route.replay.ReplayHttpModule
import route.social.SocialHttpModule

object HttpApiModules {
  def routes(
    services: HttpApiServices,
    webSocketBuilder: Option[WebSocketBuilder2[IO]] = None
  ): HttpRoutes[IO] =
    HealthHttpModule.routes(services.healthService) <+>
      IdentityHttpModule.routes(services.identityService) <+>
      MailHttpModule.routes(services.mailService) <+>
      SocialHttpModule.routes(services.friendRequestService) <+>
      ForumHttpModule.routes(services.forumService) <+>
      GovernanceHttpModule.routes(services.governanceServices) <+>
      ReplayHttpModule.routes(services.replayService) <+>
      BotProfileHttpModule.routes(services.botProfileService) <+>
      BattleHttp4sRoutes.routes(
        services.battleRuntimeContext,
        services.identityService,
        services.battleConnectionResource,
        webSocketBuilder
      )

}
