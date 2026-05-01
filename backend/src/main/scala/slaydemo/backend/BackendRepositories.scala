package slaydemo.backend

import slaydemo.backend.battle.database.{
  BattleResultRepository,
  InMemoryBattleResultRepository,
  PostgresBattleResultRepository
}
import slaydemo.backend.bots.database.{BotProfileRepository, InMemoryBotProfileRepository, PostgresBotProfileRepository}
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.forum.database.{ForumRepository, InMemoryForumRepository, PostgresForumRepository}
import slaydemo.backend.governance.database.{GovernanceRepository, InMemoryGovernanceRepository, PostgresGovernanceRepository}
import slaydemo.backend.identity.database.{
  IdentityAccountRepository,
  InMemoryIdentityAccountRepository,
  PostgresIdentityAccountRepository
}
import slaydemo.backend.mail.database.{InMemoryMailRepository, MailRepository, PostgresMailRepository}
import slaydemo.backend.replay.database.{InMemoryReplayRepository, PostgresReplayRepository, ReplayRepository}
import slaydemo.backend.shared.storage.{PostgresConnectionSettings, StorageConfig}
import slaydemo.backend.social.database.{
  FriendRequestRepository,
  InMemoryFriendRequestRepository,
  PostgresFriendRequestRepository
}

private[backend] final case class BackendRepositories(
  identity: IdentityAccountRepository,
  battleResults: BattleResultRepository,
  mail: MailRepository,
  botProfiles: BotProfileRepository,
  replay: ReplayRepository,
  friendRequests: FriendRequestRepository,
  forum: ForumRepository,
  governance: GovernanceRepository
)

private[backend] final case class BackendRepositoryFactories(
  inMemoryIdentity: () => IdentityAccountRepository,
  postgresIdentity: PostgresConnectionSettings => IdentityAccountRepository,
  inMemoryBattleResults: () => BattleResultRepository,
  postgresBattleResults: PostgresConnectionSettings => BattleResultRepository,
  inMemoryMail: () => MailRepository,
  postgresMail: PostgresConnectionSettings => MailRepository,
  inMemoryBotProfiles: () => BotProfileRepository,
  postgresBotProfiles: PostgresConnectionSettings => BotProfileRepository,
  inMemoryReplay: () => ReplayRepository,
  postgresReplay: PostgresConnectionSettings => ReplayRepository,
  inMemoryFriendRequests: () => FriendRequestRepository,
  postgresFriendRequests: PostgresConnectionSettings => FriendRequestRepository,
  inMemoryForum: () => ForumRepository,
  postgresForum: PostgresConnectionSettings => ForumRepository,
  inMemoryGovernance: () => GovernanceRepository,
  postgresGovernance: PostgresConnectionSettings => GovernanceRepository
)

private[backend] object BackendRepositoryFactories {
  val live: BackendRepositoryFactories =
    BackendRepositoryFactories(
      inMemoryIdentity = () => new InMemoryIdentityAccountRepository(),
      postgresIdentity = connection => PostgresIdentityAccountRepository(connection),
      inMemoryBattleResults = () => InMemoryBattleResultRepository(),
      postgresBattleResults = connection => PostgresBattleResultRepository(connection),
      inMemoryMail = () => InMemoryMailRepository(),
      postgresMail = connection => PostgresMailRepository(connection),
      inMemoryBotProfiles = () => InMemoryBotProfileRepository(DemoBotProfiles.all),
      postgresBotProfiles = connection => PostgresBotProfileRepository(connection),
      inMemoryReplay = () => InMemoryReplayRepository(),
      postgresReplay = connection => PostgresReplayRepository(connection),
      inMemoryFriendRequests = () => InMemoryFriendRequestRepository(),
      postgresFriendRequests = connection => PostgresFriendRequestRepository(connection),
      inMemoryForum = () => InMemoryForumRepository(),
      postgresForum = connection => PostgresForumRepository(connection),
      inMemoryGovernance = () => InMemoryGovernanceRepository(),
      postgresGovernance = connection => PostgresGovernanceRepository(connection)
    )
}

private[backend] object BackendRepositories {
  def fromStorage(
    storage: StorageConfig,
    factories: BackendRepositoryFactories = BackendRepositoryFactories.live
  ): BackendRepositories =
    storage match {
      case StorageConfig.InMemory =>
        BackendRepositories(
          identity = factories.inMemoryIdentity(),
          battleResults = factories.inMemoryBattleResults(),
          mail = factories.inMemoryMail(),
          botProfiles = factories.inMemoryBotProfiles(),
          replay = factories.inMemoryReplay(),
          friendRequests = factories.inMemoryFriendRequests(),
          forum = factories.inMemoryForum(),
          governance = factories.inMemoryGovernance()
        )
      case StorageConfig.Postgres(connection) =>
        BackendRepositories(
          identity = factories.postgresIdentity(connection),
          battleResults = factories.postgresBattleResults(connection),
          mail = factories.postgresMail(connection),
          botProfiles = factories.postgresBotProfiles(connection),
          replay = factories.postgresReplay(connection),
          friendRequests = factories.postgresFriendRequests(connection),
          forum = factories.postgresForum(connection),
          governance = factories.postgresGovernance(connection)
        )
      case StorageConfig.File(_) =>
        throw IllegalArgumentException("SLAY_DEMO_STORAGE_MODE=file is not implemented in the rebuilt backend yet.")
    }
}
