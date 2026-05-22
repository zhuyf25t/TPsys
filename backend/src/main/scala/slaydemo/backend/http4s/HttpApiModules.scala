package slaydemo.backend.http4s

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import slaydemo.backend.http4s.battle.BattleHttpModule
import slaydemo.backend.http4s.bots.BotProfileHttpModule
import slaydemo.backend.http4s.forum.ForumHttpModule
import slaydemo.backend.http4s.governance.GovernanceHttpModule
import slaydemo.backend.http4s.health.HealthHttpModule
import slaydemo.backend.http4s.identity.IdentityHttpModule
import slaydemo.backend.http4s.mail.MailHttpModule
import slaydemo.backend.http4s.replay.ReplayHttpModule
import slaydemo.backend.http4s.social.SocialHttpModule

object HttpApiModules {
  def routes(services: HttpApiServices): HttpRoutes[IO] =
    HealthHttpModule.routes(services.healthService) <+>
      IdentityHttpModule.routes(services.identityService) <+>
      MailHttpModule.routes(services.mailService) <+>
      SocialHttpModule.routes(services.friendRequestService) <+>
      ForumHttpModule.routes(services.forumService) <+>
      GovernanceHttpModule.routes(
        services.contributionAdjustmentService,
        services.governanceNotificationService
      ) <+>
      ReplayHttpModule.routes(services.replayService) <+>
      BotProfileHttpModule.routes(services.botProfileService) <+>
      BattleHttpModule.routes(services.battleServices)

}
