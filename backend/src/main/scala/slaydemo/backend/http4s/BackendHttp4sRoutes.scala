package slaydemo.backend.http4s

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleStateService,
  BattleResultService
}
import slaydemo.backend.http4s.battle.BattleHttpModule
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import slaydemo.backend.identity.services.IdentityService
import slaydemo.backend.mail.services.MailService
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.social.services.FriendRequestService
import slaydemo.backend.shared.services.HealthService

object BackendHttp4sRoutes {
  def backendRoutes(
    healthService: HealthService,
    replayService: ReplayService,
    battleQueueService: BattleQueueService,
    battleJoinAuthorizationService: BattleQueueJoinAuthorizationService,
    battleResultService: BattleResultService,
    battleStateService: BattleStateService,
    botProfileService: BotProfileService,
    identityService: IdentityService,
    mailService: MailService,
    friendRequestService: FriendRequestService,
    forumService: ForumService,
    contributionAdjustmentService: ContributionAdjustmentService,
    governanceNotificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    HealthHttp4sRoutes.routes(healthService) <+>
      IdentityHttp4sRoutes.routes(identityService) <+>
      MailHttp4sRoutes.routes(mailService) <+>
      SocialHttp4sRoutes.routes(friendRequestService) <+>
      ForumHttp4sRoutes.routes(forumService) <+>
      GovernanceHttp4sRoutes.routes(contributionAdjustmentService, governanceNotificationService) <+>
      ReplayHttp4sRoutes.catalogRoutes(replayService) <+>
      BotProfileHttp4sRoutes.routes(botProfileService) <+>
      BattleHttpModule.routes(
        battleQueueService,
        battleJoinAuthorizationService,
        battleResultService,
        battleStateService
      )

}
