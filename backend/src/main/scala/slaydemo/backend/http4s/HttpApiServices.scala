package slaydemo.backend.http4s

import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import slaydemo.backend.http4s.battle.BattleHttpServices
import slaydemo.backend.identity.services.IdentityService
import slaydemo.backend.mail.services.MailService
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.services.HealthService
import slaydemo.backend.social.services.FriendRequestService

private[http4s] final case class HttpApiServices(
  healthService: HealthService,
  replayService: ReplayService,
  battleServices: BattleHttpServices,
  botProfileService: BotProfileService,
  identityService: IdentityService,
  mailService: MailService,
  friendRequestService: FriendRequestService,
  forumService: ForumService,
  contributionAdjustmentService: ContributionAdjustmentService,
  governanceNotificationService: GovernanceNotificationService
)
