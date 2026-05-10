package slaydemo.backend

import slaydemo.backend.battle.database.{FileBattleResultRepository, InMemoryBattleResultRepository, PostgresBattleResultRepository}
import slaydemo.backend.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository, PostgresBotProfileRepository}
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.forum.database.{FileForumRepository, InMemoryForumRepository, PostgresForumRepository}
import slaydemo.backend.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository, PostgresGovernanceRepository}
import slaydemo.backend.identity.database.{
  FileIdentityAccountRepository,
  InMemoryIdentityAccountRepository,
  PostgresIdentityAccountRepository
}
import slaydemo.backend.mail.database.{FileMailRepository, InMemoryMailRepository, PostgresMailRepository}
import slaydemo.backend.replay.database.{FileReplayRepository, InMemoryReplayRepository, PostgresReplayRepository}
import slaydemo.backend.social.database.{
  FileFriendRequestRepository,
  InMemoryFriendRequestRepository,
  PostgresFriendRequestRepository
}

private[backend] object BackendLiveRepositoryFactories {
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
