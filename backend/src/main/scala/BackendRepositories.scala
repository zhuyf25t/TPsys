package services

import java.nio.file.{Path, Paths}

import services.bots.database.BotProfileRepository
import services.forum.database.ForumRepository
import services.governance.database.GovernanceRepository
import services.identity.database.IdentityAccountRepository
import services.mail.database.MailRepository
import services.replay.database.ReplayRepository
import system.storage.{PostgresConnectionSettings, StorageConfig}
import services.social.database.FriendRequestRepository

final case class BackendRepositories(
  identity: IdentityAccountRepository,
  mail: MailRepository,
  botProfiles: BotProfileRepository,
  replay: ReplayRepository,
  friendRequests: FriendRequestRepository,
  forum: ForumRepository,
  governance: GovernanceRepository
)

final case class BackendRepositoryFactories(
  inMemoryIdentity: () => IdentityAccountRepository,
  fileIdentity: Path => IdentityAccountRepository,
  postgresIdentity: PostgresConnectionSettings => IdentityAccountRepository,
  inMemoryMail: () => MailRepository,
  fileMail: Path => MailRepository,
  postgresMail: PostgresConnectionSettings => MailRepository,
  inMemoryBotProfiles: () => BotProfileRepository,
  fileBotProfiles: Path => BotProfileRepository,
  postgresBotProfiles: PostgresConnectionSettings => BotProfileRepository,
  inMemoryReplay: () => ReplayRepository,
  fileReplay: Path => ReplayRepository,
  postgresReplay: PostgresConnectionSettings => ReplayRepository,
  inMemoryFriendRequests: () => FriendRequestRepository,
  fileFriendRequests: Path => FriendRequestRepository,
  postgresFriendRequests: PostgresConnectionSettings => FriendRequestRepository,
  inMemoryForum: () => ForumRepository,
  fileForum: Path => ForumRepository,
  postgresForum: PostgresConnectionSettings => ForumRepository,
  inMemoryGovernance: () => GovernanceRepository,
  fileGovernance: Path => GovernanceRepository,
  postgresGovernance: PostgresConnectionSettings => GovernanceRepository
)

object BackendRepositoryFactories {
  val live: BackendRepositoryFactories =
    BackendLiveRepositoryFactories.live
}

object BackendRepositories {
  def fromStorage(
    storage: StorageConfig,
    factories: BackendRepositoryFactories = BackendRepositoryFactories.live
  ): BackendRepositories =
    storage match {
      case StorageConfig.InMemory =>
        BackendRepositories(
          identity = factories.inMemoryIdentity(),
          mail = factories.inMemoryMail(),
          botProfiles = factories.inMemoryBotProfiles(),
          replay = factories.inMemoryReplay(),
          friendRequests = factories.inMemoryFriendRequests(),
          forum = factories.inMemoryForum(),
          governance = factories.inMemoryGovernance()
        )
      case StorageConfig.File(root) =>
        val rootPath = Paths.get(root.value)
        BackendRepositories(
          identity = factories.fileIdentity(rootPath),
          mail = factories.fileMail(rootPath),
          botProfiles = factories.fileBotProfiles(rootPath),
          replay = factories.fileReplay(rootPath),
          friendRequests = factories.fileFriendRequests(rootPath),
          forum = factories.fileForum(rootPath),
          governance = factories.fileGovernance(rootPath)
        )
      case StorageConfig.Postgres(connection) =>
        BackendRepositories(
          identity = factories.postgresIdentity(connection),
          mail = factories.postgresMail(connection),
          botProfiles = factories.postgresBotProfiles(connection),
          replay = factories.postgresReplay(connection),
          friendRequests = factories.postgresFriendRequests(connection),
          forum = factories.postgresForum(connection),
          governance = factories.postgresGovernance(connection)
        )
    }
}
