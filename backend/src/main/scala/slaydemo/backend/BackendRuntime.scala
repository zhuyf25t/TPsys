package slaydemo.backend

import slaydemo.backend.battle.objects.DurationMillis
import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleResultService,
  BattleStateService,
  DefaultBattleFinishProjector,
  DefaultBattleQueueJoinAuthorizationService,
  DefaultBattleResultService,
  InMemoryBattleQueueService,
  InMemoryBattleStateService
}
import slaydemo.backend.bots.services.{BotProfileService, DefaultBotProfileService}
import slaydemo.backend.forum.services.{DefaultForumService, ForumService}
import slaydemo.backend.governance.services.{
  ContributionAdjustmentService,
  DefaultGovernanceService,
  GovernanceNotificationService
}
import slaydemo.backend.identity.ports.{Pbkdf2PasswordHasher, UuidIdentityIdGenerator, UuidSessionTokenGenerator}
import slaydemo.backend.identity.services.{DefaultIdentityService, IdentityService}
import slaydemo.backend.mail.services.{DefaultMailService, MailService}
import slaydemo.backend.replay.services.{DefaultReplayService, ReplayService}
import slaydemo.backend.shared.objects.ServiceName
import slaydemo.backend.shared.services.{HealthService, StaticHealthService}
import slaydemo.backend.social.services.{DefaultFriendRequestService, FriendRequestService}

private[backend] final case class BackendRuntime(
  config: BackendConfig,
  healthService: HealthService,
  battleQueueService: BattleQueueService,
  battleJoinAuthorizationService: BattleQueueJoinAuthorizationService,
  battleResultService: BattleResultService,
  battleStateService: BattleStateService,
  identityService: IdentityService,
  replayService: ReplayService,
  mailService: MailService,
  botProfileService: BotProfileService,
  friendRequestService: FriendRequestService,
  forumService: ForumService,
  contributionAdjustmentService: ContributionAdjustmentService,
  governanceNotificationService: GovernanceNotificationService
)

private[backend] object BackendRuntime {
  def fromEnvironment(env: Map[String, String]): BackendRuntime = {
    val config = BackendConfig.unsafeFromEnvironment(env)
    val healthService = StaticHealthService(ServiceName.Backend, config.port, config.storage.mode)
    val repositories = BackendRepositories.fromStorage(config.storage)
    val identityService = DefaultIdentityService(
      repository = repositories.identity,
      identityIdGenerator = UuidIdentityIdGenerator(),
      sessionTokenGenerator = UuidSessionTokenGenerator(),
      passwordHasher = Pbkdf2PasswordHasher()
    )
    val battleQueueService = InMemoryBattleQueueService()
    val battleJoinAuthorizationService = DefaultBattleQueueJoinAuthorizationService(identityService)
    val battleResultService = DefaultBattleResultService(repositories.battleResults)
    val replayService = DefaultReplayService(repositories.replay, () => System.currentTimeMillis())
    val mailService = DefaultMailService(repositories.mail, () => System.currentTimeMillis())
    val battleFinishProjector = DefaultBattleFinishProjector(
      battleResultRepository = repositories.battleResults,
      replayRepository = repositories.replay,
      mailRepository = repositories.mail
    )
    val battleStateService = InMemoryBattleStateService(
      battleQueueService,
      battleDurationFor(env),
      battleFinishProjector,
      battleQueueService
    )
    val botProfileService = DefaultBotProfileService(repositories.botProfiles)
    val friendRequestService = DefaultFriendRequestService(
      repositories.friendRequests,
      repositories.mail,
      () => System.currentTimeMillis()
    )
    val forumService = DefaultForumService(repositories.forum, () => System.currentTimeMillis())
    val governanceService = DefaultGovernanceService(repositories.governance, repositories.mail, () => System.currentTimeMillis())

    BackendRuntime(
      config = config,
      healthService = healthService,
      battleQueueService = battleQueueService,
      battleJoinAuthorizationService = battleJoinAuthorizationService,
      battleResultService = battleResultService,
      battleStateService = battleStateService,
      identityService = identityService,
      replayService = replayService,
      mailService = mailService,
      botProfileService = botProfileService,
      friendRequestService = friendRequestService,
      forumService = forumService,
      contributionAdjustmentService = governanceService,
      governanceNotificationService = governanceService
    )
  }

  private def battleDurationFor(env: Map[String, String]): DurationMillis =
    env
      .get("SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS")
      .flatMap(value => value.trim.toLongOption)
      .filter(_ > 0L)
      .map(DurationMillis.apply)
      .getOrElse(InMemoryBattleStateService.DefaultBattleDuration)
}
