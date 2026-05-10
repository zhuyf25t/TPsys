package slaydemo.backend

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

import slaydemo.backend.battle.database.{FileBattleResultRepository, InMemoryBattleResultRepository}
import slaydemo.backend.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository}
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.forum.database.{FileForumRepository, InMemoryForumRepository}
import slaydemo.backend.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository}
import slaydemo.backend.identity.database.{FileIdentityAccountRepository, InMemoryIdentityAccountRepository}
import slaydemo.backend.mail.database.{FileMailRepository, InMemoryMailRepository}
import slaydemo.backend.replay.database.{FileReplayRepository, InMemoryReplayRepository}
import slaydemo.backend.shared.storage.{
  DatabasePassword,
  DatabaseUser,
  JdbcUrl,
  PostgresConnectionSettings,
  StorageConfig,
  StorageRoot
}
import slaydemo.backend.social.database.{FileFriendRequestRepository, InMemoryFriendRequestRepository}

object BackendRepositoryWiringContractTest {
  def main(args: Array[String]): Unit = {
    memoryModeUsesOnlyMemoryFactories()
    postgresModeUsesOnlyPostgresFactories()
    fileModeUsesOnlyFileFactories()
    fileModeLiveFactoriesConstructFileRepositories()

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

  private def fileModeUsesOnlyFileFactories(): Unit = {
    val log = FactoryCallLog()
    val root = StorageRoot("./data")
    BackendRepositories.fromStorage(StorageConfig.File(root), countingFactories(log))

    assertEquals("file mode must not call memory factories", log.memoryCalls, Vector.empty)
    assertEquals("file mode must not call postgres factories", log.postgresCalls, Vector.empty)
    assertEquals("file repositories are all constructed", log.fileCalls, RepositoryNames)
    assertEquals("every file factory receives the selected root", log.fileRoots, RepositoryNames.map(_ => Paths.get(root.value)))
  }

  private def fileModeLiveFactoriesConstructFileRepositories(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-repository-file-wiring-contract")
    try {
      val repositories = BackendRepositories.fromStorage(StorageConfig.File(StorageRoot(directory.toString)))

      assert(repositories.identity.isInstanceOf[FileIdentityAccountRepository], "identity repository is file-backed")
      assert(repositories.battleResults.isInstanceOf[FileBattleResultRepository], "battle result repository is file-backed")
      assert(repositories.mail.isInstanceOf[FileMailRepository], "mail repository is file-backed")
      assert(repositories.botProfiles.isInstanceOf[FileBotProfileRepository], "bot profile repository is file-backed")
      assert(repositories.replay.isInstanceOf[FileReplayRepository], "replay repository is file-backed")
      assert(repositories.friendRequests.isInstanceOf[FileFriendRequestRepository], "friend repository is file-backed")
      assert(repositories.forum.isInstanceOf[FileForumRepository], "forum repository is file-backed")
      assert(repositories.governance.isInstanceOf[FileGovernanceRepository], "governance repository is file-backed")
    } finally {
      deleteRecursively(directory)
    }
  }

  private def countingFactories(log: FactoryCallLog): BackendRepositoryFactories =
    BackendRepositoryFactories(
      inMemoryIdentity = () => {
        log.recordMemory("identity")
        new InMemoryIdentityAccountRepository()
      },
      fileIdentity = root => {
        log.recordFile("identity", root)
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
      fileBattleResults = root => {
        log.recordFile("battle-results", root)
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
      fileMail = root => {
        log.recordFile("mail", root)
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
      fileBotProfiles = root => {
        log.recordFile("bot-profiles", root)
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
      fileReplay = root => {
        log.recordFile("replay", root)
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
      fileFriendRequests = root => {
        log.recordFile("friend-requests", root)
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
      fileForum = root => {
        log.recordFile("forum", root)
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
      fileGovernance = root => {
        log.recordFile("governance", root)
        InMemoryGovernanceRepository()
      },
      postgresGovernance = settings => {
        log.recordPostgres("governance", settings)
        InMemoryGovernanceRepository()
      }
    )

  private final class FactoryCallLog {
    private var memoryNames: Vector[String] = Vector.empty
    private var fileNames: Vector[String] = Vector.empty
    private var selectedFileRoots: Vector[Path] = Vector.empty
    private var postgresNames: Vector[String] = Vector.empty
    private var selectedSettings: Vector[PostgresConnectionSettings] = Vector.empty

    def memoryCalls: Vector[String] =
      memoryNames

    def postgresCalls: Vector[String] =
      postgresNames

    def fileCalls: Vector[String] =
      fileNames

    def postgresSettings: Vector[PostgresConnectionSettings] =
      selectedSettings

    def fileRoots: Vector[Path] =
      selectedFileRoots

    def recordMemory(name: String): Unit =
      memoryNames = memoryNames :+ name

    def recordFile(name: String, root: Path): Unit = {
      fileNames = fileNames :+ name
      selectedFileRoots = selectedFileRoots :+ root
    }

    def recordPostgres(name: String, settings: PostgresConnectionSettings): Unit = {
      postgresNames = postgresNames :+ name
      selectedSettings = selectedSettings :+ settings
    }
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def deleteRecursively(path: Path): Unit =
    if Files.exists(path) then {
      val stream = Files.walk(path)
      try {
        stream
          .iterator()
          .asScala
          .toVector
          .sortBy(_.toString.length)
          .reverse
          .foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

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
