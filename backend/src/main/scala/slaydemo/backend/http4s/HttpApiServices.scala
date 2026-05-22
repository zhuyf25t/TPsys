package slaydemo.backend.http4s

import slaydemo.backend.BackendRuntime
import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleResultService,
  BattleStateService
}
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import slaydemo.backend.identity.services.IdentityService
import slaydemo.backend.mail.services.MailService
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.services.HealthService
import slaydemo.backend.social.services.FriendRequestService

private[http4s] final case class HttpApiServices(
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
)

private[http4s] object HttpApiServices {
  def fromRuntime(runtime: BackendRuntime): HttpApiServices =
    HttpApiServices(
      healthService = runtime.healthService,
      replayService = runtime.replayService,
      battleQueueService = runtime.battleQueueService,
      battleJoinAuthorizationService = runtime.battleJoinAuthorizationService,
      battleResultService = runtime.battleResultService,
      battleStateService = runtime.battleStateService,
      botProfileService = runtime.botProfileService,
      identityService = runtime.identityService,
      mailService = runtime.mailService,
      friendRequestService = runtime.friendRequestService,
      forumService = runtime.forumService,
      contributionAdjustmentService = runtime.contributionAdjustmentService,
      governanceNotificationService = runtime.governanceNotificationService
    )
}
