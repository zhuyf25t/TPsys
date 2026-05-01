package slaydemo.backend

import slaydemo.backend.battle.database.InMemoryBattleResultRepository
import slaydemo.backend.bots.database.InMemoryBotProfileRepository
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.forum.database.InMemoryForumRepository
import slaydemo.backend.governance.database.InMemoryGovernanceRepository
import slaydemo.backend.identity.database.InMemoryIdentityAccountRepository
import slaydemo.backend.mail.database.InMemoryMailRepository
import slaydemo.backend.replay.database.InMemoryReplayRepository
import slaydemo.backend.shared.storage.{
  DatabasePassword,
  DatabaseUser,
  JdbcUrl,
  PostgresConnectionSettings,
  StorageConfig,
  StorageRoot
}
import slaydemo.backend.social.database.InMemoryFriendRequestRepository

object BackendRepositoryWiringContractTest {
  def main(args: Array[String]): Unit = {
    memoryModeUsesOnlyMemoryFactories()
    postgresModeUsesOnlyPostgresFactories()
    fileModeRejectsWithoutConstructingRepositories()

    println("BackendRepository wiring contract checks passed")
  }

  private def memoryModeUsesOnlyMemoryFactories(): Unit = {
    val log = FactoryCallLog()
    val repositories = BackendRepositories.fromStorage(StorageConfig.InMemory, countingFactories(log))

    assertEquals("memory repositories are all constructed", log.memoryCalls, RepositoryNames)
    assertEquals("memory mode must not call postgres factories", log.postgresCalls, Vector.empty)
    assert(repositories.identity.isInstanceOf[InMemoryIdentityAccountRepository], "identity repository is in-memory")
    assert(repositories.battleResults.isInstanceOf[InMemoryBattleResultRepository], "battle result repository is in-memory")
    assert(repositories.mail.isInstanceOf[InMemoryMailRepository], "mail repository is in-memory")
    assert(repositories.botProfiles.isInstanceOf[InMemoryBotProfileRepository], "bot profile repository is in-memory")
    assert(repositories.replay.isInstanceOf[InMemoryReplayRepository], "replay repository is in-memory")
    assert(repositories.friendRequests.isInstanceOf[InMemoryFriendRequestRepository], "friend repository is in-memory")
    assert(repositories.forum.isInstanceOf[InMemoryForumRepository], "forum repository is in-memory")
    assert(repositories.governance.isInstanceOf[InMemoryGovernanceRepository], "governance repository is in-memory")
  }

  private def postgresModeUsesOnlyPostgresFactories(): Unit = {
    val settings = PostgresConnectionSettings(
      jdbcUrl = JdbcUrl("jdbc:postgresql://localhost:5432/slay_demo_contract"),
      user = Some(DatabaseUser("contract_user")),
      password = DatabasePassword.fromString("contract-secret")
    )
    val log = FactoryCallLog()

    BackendRepositories.fromStorage(StorageConfig.Postgres(settings), countingFactories(log))

    assertEquals("postgres mode must not call memory factories", log.memoryCalls, Vector.empty)
    assertEquals("postgres repositories are all constructed", log.postgresCalls, RepositoryNames)
    assertEquals("every postgres factory receives the selected settings", log.postgresSettings, RepositoryNames.map(_ => settings))
  }

  private def fileModeRejectsWithoutConstructingRepositories(): Unit = {
    val log = FactoryCallLog()
    val message = interceptMessage {
      BackendRepositories.fromStorage(StorageConfig.File(StorageRoot("./data")), countingFactories(log))
    }

    assertContains("file mode message", message, "SLAY_DEMO_STORAGE_MODE=file is not implemented")
    assertEquals("file mode must not call memory factories", log.memoryCalls, Vector.empty)
    assertEquals("file mode must not call postgres factories", log.postgresCalls, Vector.empty)
  }

  private def countingFactories(log: FactoryCallLog): BackendRepositoryFactories =
    BackendRepositoryFactories(
      inMemoryIdentity = () => {
        log.recordMemory("identity")
        new InMemoryIdentityAccountRepository()
      },
      postgresIdentity = settings => {
        log.recordPostgres("identity", settings)
        new InMemoryIdentityAccountRepository()
      },
      inMemoryBattleResults = () => {
        log.recordMemory("battle-results")
        InMemoryBattleResultRepository()
      },
      postgresBattleResults = settings => {
        log.recordPostgres("battle-results", settings)
        InMemoryBattleResultRepository()
      },
      inMemoryMail = () => {
        log.recordMemory("mail")
        InMemoryMailRepository()
      },
      postgresMail = settings => {
        log.recordPostgres("mail", settings)
        InMemoryMailRepository()
      },
      inMemoryBotProfiles = () => {
        log.recordMemory("bot-profiles")
        InMemoryBotProfileRepository(DemoBotProfiles.all)
      },
      postgresBotProfiles = settings => {
        log.recordPostgres("bot-profiles", settings)
        InMemoryBotProfileRepository(DemoBotProfiles.all)
      },
      inMemoryReplay = () => {
        log.recordMemory("replay")
        InMemoryReplayRepository()
      },
      postgresReplay = settings => {
        log.recordPostgres("replay", settings)
        InMemoryReplayRepository()
      },
      inMemoryFriendRequests = () => {
        log.recordMemory("friend-requests")
        InMemoryFriendRequestRepository()
      },
      postgresFriendRequests = settings => {
        log.recordPostgres("friend-requests", settings)
        InMemoryFriendRequestRepository()
      },
      inMemoryForum = () => {
        log.recordMemory("forum")
        InMemoryForumRepository()
      },
      postgresForum = settings => {
        log.recordPostgres("forum", settings)
        InMemoryForumRepository()
      },
      inMemoryGovernance = () => {
        log.recordMemory("governance")
        InMemoryGovernanceRepository()
      },
      postgresGovernance = settings => {
        log.recordPostgres("governance", settings)
        InMemoryGovernanceRepository()
      }
    )

  private final class FactoryCallLog {
    private var memoryNames: Vector[String] = Vector.empty
    private var postgresNames: Vector[String] = Vector.empty
    private var selectedSettings: Vector[PostgresConnectionSettings] = Vector.empty

    def memoryCalls: Vector[String] =
      memoryNames

    def postgresCalls: Vector[String] =
      postgresNames

    def postgresSettings: Vector[PostgresConnectionSettings] =
      selectedSettings

    def recordMemory(name: String): Unit =
      memoryNames = memoryNames :+ name

    def recordPostgres(name: String, settings: PostgresConnectionSettings): Unit = {
      postgresNames = postgresNames :+ name
      selectedSettings = selectedSettings :+ settings
    }
  }

  private def interceptMessage(block: => Unit): String =
    try {
      block
      fail("expected exception")
    } catch {
      case error: IllegalArgumentException => error.getMessage
    }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, text: String, expectedPart: String): Unit =
    assert(text.contains(expectedPart), s"$label: expected to find $expectedPart in $text")

  private def fail(message: String): Nothing =
    throw AssertionError(message)

  private val RepositoryNames: Vector[String] =
    Vector(
      "identity",
      "battle-results",
      "mail",
      "bot-profiles",
      "replay",
      "friend-requests",
      "forum",
      "governance"
    )
}
