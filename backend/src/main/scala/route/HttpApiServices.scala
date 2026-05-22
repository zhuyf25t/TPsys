package route

import services.bots.services.BotProfileService
import services.forum.services.ForumService
import route.governance.GovernanceHttpServices
import services.battle.routes.BattleAPIMessageServices
import services.identity.services.IdentityService
import services.mail.services.MailService
import services.replay.services.ReplayService
import system.services.HealthService
import services.social.services.FriendRequestService

private[route] final case class HttpApiServices(
  healthService: HealthService,
  replayService: ReplayService,
  battleServices: BattleAPIMessageServices,
  botProfileService: BotProfileService,
  identityService: IdentityService,
  mailService: MailService,
  friendRequestService: FriendRequestService,
  forumService: ForumService,
  governanceServices: GovernanceHttpServices
)
