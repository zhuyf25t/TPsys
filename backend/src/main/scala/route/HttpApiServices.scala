package route

import cats.effect.{IO, Resource}
import services.bots.services.BotProfileService
import services.forum.services.ForumService
import route.governance.GovernanceHttpServices
import services.battle.routes.BattleAPIRuntimeContext
import services.identity.services.IdentityService
import services.mail.services.MailService
import services.replay.services.ReplayService
import system.services.HealthService
import services.social.services.FriendRequestService

import java.sql.Connection

private[route] final case class HttpApiServices(
  healthService: HealthService,
  replayService: ReplayService,
  battleRuntimeContext: BattleAPIRuntimeContext,
  battleConnectionResource: Resource[IO, Connection],
  botProfileService: BotProfileService,
  identityService: IdentityService,
  mailService: MailService,
  friendRequestService: FriendRequestService,
  forumService: ForumService,
  governanceServices: GovernanceHttpServices
)
