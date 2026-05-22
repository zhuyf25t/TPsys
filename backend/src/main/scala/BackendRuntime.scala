package services

import services.battle.objects.DurationMillis
import services.battle.application.{
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
import services.battle.ports.{BattleMailPublisherPort, BattleReplayWriterPort}
import services.bots.services.{BotProfileService, DefaultBotProfileService}
import services.forum.services.{DefaultForumService, ForumService}
import services.governance.services.{
  ContributionAdjustmentService,
  DefaultGovernanceService,
  GovernanceNotificationService
}
import services.identity.ports.{Pbkdf2PasswordHasher, UuidIdentityIdGenerator, UuidSessionTokenGenerator}
import services.identity.services.{DefaultIdentityService, IdentityService}
import services.mail.services.{DefaultMailService, MailService}
import services.replay.services.{DefaultReplayService, ReplayService}
import system.objects.ServiceName
import system.services.{HealthService, StaticHealthService}
import services.social.services.{DefaultFriendRequestService, FriendRequestService}

final case class BackendRuntime(
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

object BackendRuntime {
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
    val battleReplayWriter = new BattleReplayWriterPort {
      override def saveReplay(record: services.replay.objects.ReplayRecord): Unit =
        repositories.replay.saveReplay(record)
    }
    val battleMailPublisher = new BattleMailPublisherPort {
      override def publish(mail: services.mail.objects.MailRecord): Unit =
        repositories.mail.save(mail)
    }
    val battleFinishProjector = DefaultBattleFinishProjector(
      battleResultRepository = repositories.battleResults,
      replayWriter = battleReplayWriter,
      mailPublisher = battleMailPublisher
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
