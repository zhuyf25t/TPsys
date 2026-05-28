package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*

import services.battle.objects.DurationMillis
import services.battle.database.abilities.{BattlePickupRuleBook, BattleSkillRuleBook}
import services.battle.database.abilities.{BattleAbilityRuleTable, BattleAbilityRuleTableInitializer}
import services.battle.database.actors.BattleBotRuleBook
import services.battle.database.actors.{BattleActorRuleTable, BattleActorRuleTableInitializer}
import services.battle.database.runtime.BattleRuntimeRuleBook
import services.battle.database.runtime.{BattleRuntimeRuleTable, BattleRuntimeRuleTableInitializer}
import services.battle.database.world.BattleWorldRuleBook
import services.battle.database.world.{BattleWorldRuleTable, BattleWorldRuleTableInitializer}
import services.battle.database.results.BattleResultTableInitializer
import services.battle.microservices.session.services.{
  BattleStateService,
  InMemoryBattleStateService
}
import services.battle.database.combat.BattleCombatRuleBook
import services.battle.database.combat.{BattleCombatRuleTable, BattleCombatRuleTableInitializer}
import services.battle.microservices.projections.services.{BattleMailPublisherPort, BattleReplayWriterPort, DefaultBattleFinishProjector}
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
  def fromEnvironment(env: Map[String, String]): BackendRuntime = {
    val config = BackendConfig.unsafeFromEnvironment(env)
    initializeBattleDynamicRules(config)
    val battleConnection = battlePostgresConnection(config)
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
      connectionSettings = battleConnection,
      replayWriter = battleReplayWriter,
      mailPublisher = battleMailPublisher
    )
    val battleStateService = InMemoryBattleStateService(
      battleQueueService,
      battleDurationFor,
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

  private def initializeBattleDynamicRules(config: BackendConfig): Unit =
    config.storage match {
      case StorageConfig.Postgres(connectionSettings) =>
        PostgresSupport.withConnection(connectionSettings) { connection =>
          val loadRules =
            for {
              _ <- BattleWorldRuleTableInitializer.initialize(connection)
              worldRules <- BattleWorldRuleTable.load(connection)
              _ <- IO.blocking(BattleWorldRuleBook.replace(worldRules))
              _ <- BattleRuntimeRuleTableInitializer.initialize(connection)
              runtimeRules <- BattleRuntimeRuleTable.load(connection)
              _ <- IO.blocking(BattleRuntimeRuleBook.replace(runtimeRules))
              _ <- BattleCombatRuleTableInitializer.initialize(connection)
              combatRules <- BattleCombatRuleTable.list(connection)
              _ <- IO.raiseWhen(combatRules.isEmpty)(
                IllegalStateException("PostgreSQL table battle_combat_weapon_rules has no rows.")
              )
              _ <- IO.blocking(BattleCombatRuleBook.replaceAll(combatRules))
              _ <- BattleAbilityRuleTableInitializer.initialize(connection)
              skillRules <- BattleAbilityRuleTable.loadRuleSet(connection)
              _ <- IO.blocking(BattleSkillRuleBook.replace(skillRules))
              pickupRules <- BattleAbilityRuleTable.loadActivePickup(connection)
              _ <- IO.blocking(BattlePickupRuleBook.replace(pickupRules))
              _ <- BattleActorRuleTableInitializer.initialize(connection)
              botRules <- BattleActorRuleTable.loadActive(connection)
              _ <- IO.blocking(BattleBotRuleBook.replace(botRules))
              _ <- IO.blocking(BattleResultTableInitializer.initialize(connection))
            } yield ()
          loadRules.unsafeRunSync()
        }
      case StorageConfig.InMemory | StorageConfig.File(_) =>
        throw IllegalStateException("Battle dynamic rules require PostgreSQL storage.")
    }

  private def battleDurationFor: DurationMillis =
    BattleRuntimeRuleBook.runtime.defaultBattleDuration

  private def battlePostgresConnection(config: BackendConfig): PostgresConnectionSettings =
    config.storage match {
      case StorageConfig.Postgres(connection) =>
        connection
      case StorageConfig.InMemory | StorageConfig.File(_) =>
        throw IllegalStateException("Battle runtime requires PostgreSQL storage.")
    }
}
