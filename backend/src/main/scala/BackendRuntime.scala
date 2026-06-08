package services

import cats.effect.{IO, Resource}
import cats.syntax.all.*

import services.battle.objects.DurationMillis
import services.battle.microservices.abilities.database.{BattleAbilityRuleTable, BattleAbilityRuleTableInitializer}
import services.battle.microservices.actors.database.{BattleActorRuleTable, BattleActorRuleTableInitializer}
import services.battle.microservices.runtime.database.{BattleRuntimeRuleTable, BattleRuntimeRuleTableInitializer}
import services.battle.microservices.world.database.{
  BattleWorldDefaultMapRuleSeeder,
  BattleWorldRuleTable,
  BattleWorldRuleTableInitializer
}
import services.battle.microservices.results.database.BattleResultTableInitializer
import services.battle.microservices.session.services.{
  BattleStateService,
  InMemoryBattleStateService
}
import services.battle.microservices.combat.database.{BattleCombatRuleTable, BattleCombatRuleTableInitializer}
import services.battle.microservices.projections.services.{BattleMailPublisherPort, BattleReplayWriterPort, DefaultBattleFinishProjector}
import services.battle.microservices.runtime.services.{BattleDynamicRuleBook, BattleDynamicRules}
import services.battle.microservices.queue.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  DefaultBattleQueueJoinAuthorizationService,
  InMemoryBattleQueueService
}
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
import system.database.PostgresSupport
import system.storage.{PostgresConnectionSettings, StorageConfig}
import services.social.services.{DefaultFriendRequestService, FriendRequestService}

final case class BackendRuntime(
  config: BackendConfig,
  healthService: HealthService,
  battleQueueService: BattleQueueService,
  battleJoinAuthorizationService: BattleQueueJoinAuthorizationService,
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
  def resource(env: Map[String, String]): Resource[IO, BackendRuntime] =
    Resource.make(fromEnvironment(env))(_ => IO.blocking(PostgresSupport.closeAll()))

  def fromEnvironment(env: Map[String, String]): IO[BackendRuntime] =
    for
      config <- IO.blocking(BackendConfig.unsafeFromEnvironment(env))
      loadedBattleRules <- loadBattleDynamicRules(config)
      battleRules <- BattleDynamicRuleBook.create(loadedBattleRules)
      runtime <- create(config, battleRules)
    yield runtime

  private def create(config: BackendConfig, battleRules: BattleDynamicRuleBook): IO[BackendRuntime] =
    InMemoryBattleQueueService.create().flatMap { battleQueueService =>
    val battleConnection = battlePostgresConnection(config)
    val healthService = StaticHealthService(ServiceName.Backend, config.port, config.storage.mode)
    val repositories = BackendRepositories.fromStorage(config.storage)
    val identityService = DefaultIdentityService(
      repository = repositories.identity,
      identityIdGenerator = UuidIdentityIdGenerator(),
      sessionTokenGenerator = UuidSessionTokenGenerator(),
      passwordHasher = Pbkdf2PasswordHasher()
    )
    val battleJoinAuthorizationService = DefaultBattleQueueJoinAuthorizationService(identityService)
    val replayService = DefaultReplayService(repositories.replay, () => System.currentTimeMillis())
    val mailService = DefaultMailService(repositories.mail, () => System.currentTimeMillis())
    val battleReplayWriter = new BattleReplayWriterPort {
      override def saveReplay(record: services.replay.objects.ReplayRecord): IO[Unit] =
        IO.blocking(repositories.replay.saveReplay(record)).map(_ => ())
    }
    val battleMailPublisher = new BattleMailPublisherPort {
      override def publish(mail: services.mail.objects.MailRecord): IO[Unit] =
        IO.blocking(repositories.mail.save(mail)).map(_ => ())
    }
    val battleFinishProjector = DefaultBattleFinishProjector(
      connectionSettings = battleConnection,
      replayWriter = battleReplayWriter,
      mailPublisher = battleMailPublisher
    )
    val botProfileService = DefaultBotProfileService(repositories.botProfiles)
    val friendRequestService = DefaultFriendRequestService(
      repositories.friendRequests,
      repositories.mail,
      () => System.currentTimeMillis()
    )
    val forumService = DefaultForumService(repositories.forum, () => System.currentTimeMillis())
    val governanceService = DefaultGovernanceService(repositories.governance, repositories.mail, () => System.currentTimeMillis())

    battleDurationFor(battleRules).flatMap { battleDuration =>
      InMemoryBattleStateService
        .create(
        battleQueueService,
        battleDuration,
        battleRules,
        battleFinishProjector,
        battleQueueService
      )
      .map { battleStateService =>
        BackendRuntime(
          config = config,
          healthService = healthService,
          battleQueueService = battleQueueService,
          battleJoinAuthorizationService = battleJoinAuthorizationService,
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
    }
    }

  private def loadBattleDynamicRules(config: BackendConfig): IO[BattleDynamicRules] =
    config.storage match {
      case StorageConfig.Postgres(connectionSettings) =>
        PostgresSupport.withConnectionIO(connectionSettings) { connection =>
          for {
            _ <- BattleWorldRuleTableInitializer.initialize(connection)
            _ <- BattleWorldRuleTable.upsertDefaultRules(connection)
            _ <- BattleWorldDefaultMapRuleSeeder.upsertDefaultMaps(connection)
            worldRules <- BattleWorldRuleTable.load(connection)
            _ <- BattleRuntimeRuleTableInitializer.initialize(connection)
            _ <- BattleRuntimeRuleTable.upsertDefaultRules(connection)
            runtimeRules <- BattleRuntimeRuleTable.load(connection)
            _ <- BattleCombatRuleTableInitializer.initialize(connection)
            _ <- BattleCombatRuleTable.upsertDefaultWeaponRules(connection)
            combatRules <- BattleCombatRuleTable.list(connection)
            _ <- BattleAbilityRuleTableInitializer.initialize(connection)
            _ <- BattleAbilityRuleTable.upsertDefaultSkillRules(connection)
            _ <- BattleAbilityRuleTable.upsertDefaultPickupRules(connection)
            skillRules <- BattleAbilityRuleTable.loadRuleSet(connection)
            pickupRules <- BattleAbilityRuleTable.loadActivePickup(connection)
            _ <- BattleActorRuleTableInitializer.initialize(connection)
            _ <- BattleActorRuleTable.upsertDefaultBotRules(connection)
            botRules <- BattleActorRuleTable.loadActive(connection)
            _ <- BattleResultTableInitializer.initialize(connection)
            battleRules <- BattleDynamicRules.fromLoaded(
              worldRuleSet = worldRules,
              runtimeRuleSet = runtimeRules,
              combatRules = combatRules,
              skillRuleSet = skillRules,
              pickupRuleConfig = pickupRules,
              botRuleConfig = botRules
            )
          } yield battleRules
        }
      case StorageConfig.InMemory | StorageConfig.File(_) =>
        IO.raiseError(IllegalStateException("Battle dynamic rules require PostgreSQL storage."))
    }

  private def battleDurationFor(battleRules: BattleDynamicRuleBook): IO[DurationMillis] =
    battleRules.runtime.map(_.defaultBattleDuration)

  private def battlePostgresConnection(config: BackendConfig): PostgresConnectionSettings =
    config.storage match {
      case StorageConfig.Postgres(connection) =>
        connection
      case StorageConfig.InMemory | StorageConfig.File(_) =>
        throw IllegalStateException("Battle runtime requires PostgreSQL storage.")
    }
}
