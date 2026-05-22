package services

import services.battle.database.{FileBattleResultRepository, InMemoryBattleResultRepository, PostgresBattleResultRepository}
import services.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository, PostgresBotProfileRepository}
import services.bots.objects.DemoBotProfiles
import services.forum.database.{FileForumRepository, InMemoryForumRepository, PostgresForumRepository}
import services.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository, PostgresGovernanceRepository}
import services.identity.database.{
  FileIdentityAccountRepository,
  InMemoryIdentityAccountRepository,
  PostgresIdentityAccountRepository
}
import services.mail.database.{FileMailRepository, InMemoryMailRepository, PostgresMailRepository}
import services.replay.database.{FileReplayRepository, InMemoryReplayRepository, PostgresReplayRepository}
import services.social.database.{
  FileFriendRequestRepository,
  InMemoryFriendRequestRepository,
  PostgresFriendRequestRepository
}

private[services] object BackendLiveRepositoryFactories {
  val live: BackendRepositoryFactories =
    BackendRepositoryFactories(
      inMemoryIdentity = () => new InMemoryIdentityAccountRepository(),
      fileIdentity = root => FileIdentityAccountRepository(root.resolve("identity-accounts.json")),
      postgresIdentity = connection => PostgresIdentityAccountRepository(connection),
      inMemoryBattleResults = () => InMemoryBattleResultRepository(),
      fileBattleResults = root => FileBattleResultRepository(root.resolve("battle-results.json")),
      postgresBattleResults = connection => PostgresBattleResultRepository(connection),
      inMemoryMail = () => InMemoryMailRepository(),
      fileMail = root => FileMailRepository(root.resolve("mails.json")),
      postgresMail = connection => PostgresMailRepository(connection),
      inMemoryBotProfiles = () => InMemoryBotProfileRepository(DemoBotProfiles.all),
      fileBotProfiles = root => FileBotProfileRepository(root.resolve("bot-profiles.json")),
      postgresBotProfiles = connection => PostgresBotProfileRepository(connection),
      inMemoryReplay = () => InMemoryReplayRepository(),
      fileReplay = root => FileReplayRepository(root.resolve("replay-records.json")),
      postgresReplay = connection => PostgresReplayRepository(connection),
      inMemoryFriendRequests = () => InMemoryFriendRequestRepository(),
      fileFriendRequests = root => FileFriendRequestRepository(root.resolve("friend-requests.json")),
      postgresFriendRequests = connection => PostgresFriendRequestRepository(connection),
      inMemoryForum = () => InMemoryForumRepository(),
      fileForum = root => FileForumRepository(root.resolve("forum.json")),
      postgresForum = connection => PostgresForumRepository(connection),
      inMemoryGovernance = () => InMemoryGovernanceRepository(),
      fileGovernance = root => FileGovernanceRepository(root),
      postgresGovernance = connection => PostgresGovernanceRepository(connection)
    )
}
