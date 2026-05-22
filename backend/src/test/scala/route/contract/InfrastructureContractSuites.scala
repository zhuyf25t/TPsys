package route.contract

import java.lang.reflect.{InvocationHandler, Method as JavaMethod, Proxy}
import java.nio.file.{Files, Path, Paths}
import java.security.SecureRandom
import java.sql.Connection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Header, Headers, HttpRoutes, Method, Request}
import org.typelevel.ci.CIString
import scala.jdk.CollectionConverters.*

import services.battle.routes.BattleHttp4sRoutes
import route.bots.BotProfileHttp4sRoutes
import route.governance.GovernanceHttp4sRoutes
import route.health.{HealthHttp4sRoutes, HealthHttpModule}
import route.identity.IdentityHttp4sRoutes
import route.mail.MailHttp4sRoutes
import route.forum.ForumHttp4sRoutes
import route.replay.{ReplayHttp4sRoutes, ReplayHttpModule}
import route.social.SocialHttp4sRoutes
import services.{BackendRepositories, BackendRepositoryFactories}
import services.battle.persistence.{BattleResultRepository, FileBattleResultRepository, InMemoryBattleResultRepository}
import services.battle.objects.*
import services.bots.objects.*
import services.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository}
import services.forum.database.{FileForumRepository, InMemoryForumRepository}
import services.forum.objects.*
import services.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository}
import services.governance.objects.*
import services.identity.database.{FileIdentityAccountRepository, InMemoryIdentityAccountRepository}
import services.identity.api.IdentityAccountSummary
import services.identity.objects.{IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import services.identity.ports.{PasswordVerification, Pbkdf2PasswordHasher, Sha256PasswordHasher}
import services.mail.database.{FileMailRepository, InMemoryMailRepository}
import services.mail.objects.*
import services.replay.database.{FileReplayRepository, InMemoryReplayRepository, ReplayRepository}
import services.replay.objects.*
import services.social.database.{FileFriendRequestRepository, InMemoryFriendRequestRepository}
import services.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import services.battle.services.{
  BattleCommandOwnership,
  BattleCommandSubmitError,
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueJoinCommand,
  BattleQueueLeaveOutcome,
  BattleQueueService,
  BattleQueueStatusError,
  BattleRoomError,
  BattleResultRecordCommand,
  BattleResultRecordError,
  BattleResultService,
  BattleFinishProjector,
  BattleRoomLifecycleSink,
  BattleSessionLookup,
  BattleSessionSeed,
  BattleStateReadError,
  BattleStateService,
  BattleFinishProjectionFailureReporter,
  BattleFinishProjectionOutcome,
  DefaultBattleFinishProjector,
  InMemoryBattleStateService,
  RealtimeRoomHeartbeatCommand
}
import services.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
  IdentitySessionError
}
import services.mail.services.{MailReadError, MailService}
import services.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  ForumCreateTopicError,
  ForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import services.governance.services.{
  ContributionAdjustmentCommand,
  ContributionAdjustmentService,
  ContributionAdjustmentSubmissionResult,
  GovernanceNotificationService,
  GovernanceReviewNotificationCommand,
  GovernanceReviewNotificationSubmissionResult
}
import services.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}
import services.bots.services.BotProfileService
import services.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestService,
  FriendRequestSubmissionResult
}
import services.identity.objects.DisplayName
import system.objects.{HealthResponse, HealthStatus}
import system.objects.{ServiceName, ServicePort, UserId}
import system.database.PostgresSupport
import system.services.HealthService
import system.storage.*

private[contract] object StorageConfigContractTest:
  def run(): Unit =
    defaultStorageIsMemory()
    genericDatabaseUrlDoesNotEnablePostgres()
    postgresModeRequiresExplicitJdbcUrl()
    postgresModeReadsExplicitConnectionSettings()
    fileModeRequiresStorageRoot()
    fileModeReadsStorageRoot()
    unsupportedModeReportsNormalizedValue()

  private def defaultStorageIsMemory(): Unit =
    ContractAssertions.assertEquals(
      "empty environment defaults to in-memory storage",
      StorageConfig.fromEnvironment(Map.empty),
      Right(StorageConfig.InMemory)
    )

  private def genericDatabaseUrlDoesNotEnablePostgres(): Unit =
    ContractAssertions.assertEquals(
      "generic DATABASE_URL is ignored unless SLAY_DEMO_STORAGE_MODE opts in",
      StorageConfig.fromEnvironment(
        Map("DATABASE_URL" -> "jdbc:postgresql://localhost:5432/slay_demo")
      ),
      Right(StorageConfig.InMemory)
    )

  private def postgresModeRequiresExplicitJdbcUrl(): Unit =
    ContractAssertions.assertEquals(
      "postgres mode requires SLAY_DEMO_DATABASE_URL",
      StorageConfig.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> "postgres")),
      Left(StorageConfigError.MissingPostgresJdbcUrl)
    )

  private def postgresModeReadsExplicitConnectionSettings(): Unit =
    StorageConfig.fromEnvironment(
      Map(
        "SLAY_DEMO_STORAGE_MODE" -> "postgres",
        "SLAY_DEMO_DATABASE_URL" -> " jdbc:postgresql://localhost:5432/slay_demo ",
        "SLAY_DEMO_DATABASE_USER" -> " slay_user ",
        "SLAY_DEMO_DATABASE_PASSWORD" -> " super-secret "
      )
    ) match {
      case Right(StorageConfig.Postgres(connection)) =>
        ContractAssertions.assertEquals(
          "postgres jdbc url is trimmed",
          connection.jdbcUrl,
          JdbcUrl("jdbc:postgresql://localhost:5432/slay_demo")
        )
        ContractAssertions.assertEquals("postgres user is trimmed", connection.user, Some(DatabaseUser("slay_user")))
        assert(
          connection.password.exists(_.toString == "<redacted>"),
          "postgres password must render as redacted"
        )
      case other =>
        throw AssertionError(s"expected postgres config, got $other")
    }

  private def fileModeRequiresStorageRoot(): Unit =
    ContractAssertions.assertEquals(
      "file mode requires SLAY_DEMO_DATA_DIR",
      StorageConfig.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> "file")),
      Left(StorageConfigError.MissingFileStorageRoot)
    )

  private def fileModeReadsStorageRoot(): Unit =
    ContractAssertions.assertEquals(
      "file mode reads and trims SLAY_DEMO_DATA_DIR",
      StorageConfig.fromEnvironment(
        Map("SLAY_DEMO_STORAGE_MODE" -> "files", "SLAY_DEMO_DATA_DIR" -> " ./data ")
      ),
      Right(StorageConfig.File(StorageRoot("./data")))
    )

  private def unsupportedModeReportsNormalizedValue(): Unit =
    ContractAssertions.assertEquals(
      "unsupported storage mode is reported after normalization",
      StorageConfig.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> " Redis ")),
      Left(StorageConfigError.UnsupportedStorageMode("redis"))
    )

private[contract] object BackendRepositoryWiringContractTest:
  def run(): Unit =
    memoryModeUsesOnlyMemoryFactories()
    postgresModeUsesOnlyPostgresFactories()
    fileModeUsesOnlyFileFactories()
    fileModeLiveFactoriesConstructFileRepositories()

  private def memoryModeUsesOnlyMemoryFactories(): Unit =
    val log = FactoryCallLog()
    val repositories = BackendRepositories.fromStorage(StorageConfig.InMemory, countingFactories(log))

    ContractAssertions.assertEquals("memory repositories are all constructed", log.memoryCalls, RepositoryNames)
    ContractAssertions.assertEquals("memory mode must not call postgres factories", log.postgresCalls, Vector.empty)
    assert(repositories.identity.isInstanceOf[InMemoryIdentityAccountRepository], "identity repository is in-memory")
    assert(repositories.battleResults.isInstanceOf[InMemoryBattleResultRepository], "battle result repository is in-memory")
    assert(repositories.mail.isInstanceOf[InMemoryMailRepository], "mail repository is in-memory")
    assert(repositories.botProfiles.isInstanceOf[InMemoryBotProfileRepository], "bot profile repository is in-memory")
    assert(repositories.replay.isInstanceOf[InMemoryReplayRepository], "replay repository is in-memory")
    assert(repositories.friendRequests.isInstanceOf[InMemoryFriendRequestRepository], "friend repository is in-memory")
    assert(repositories.forum.isInstanceOf[InMemoryForumRepository], "forum repository is in-memory")
    assert(repositories.governance.isInstanceOf[InMemoryGovernanceRepository], "governance repository is in-memory")

  private def postgresModeUsesOnlyPostgresFactories(): Unit =
    val settings = PostgresConnectionSettings(
      jdbcUrl = JdbcUrl("jdbc:postgresql://localhost:5432/slay_demo_contract"),
      user = Some(DatabaseUser("contract_user")),
      password = DatabasePassword.fromString("contract-secret")
    )
    val log = FactoryCallLog()

    BackendRepositories.fromStorage(StorageConfig.Postgres(settings), countingFactories(log))

    ContractAssertions.assertEquals("postgres mode must not call memory factories", log.memoryCalls, Vector.empty)
    ContractAssertions.assertEquals("postgres repositories are all constructed", log.postgresCalls, RepositoryNames)
    ContractAssertions.assertEquals(
      "every postgres factory receives the selected settings",
      log.postgresSettings,
      RepositoryNames.map(_ => settings)
    )

  private def fileModeUsesOnlyFileFactories(): Unit =
    val log = FactoryCallLog()
    val root = StorageRoot("./data")
    BackendRepositories.fromStorage(StorageConfig.File(root), countingFactories(log))

    ContractAssertions.assertEquals("file mode must not call memory factories", log.memoryCalls, Vector.empty)
    ContractAssertions.assertEquals("file mode must not call postgres factories", log.postgresCalls, Vector.empty)
    ContractAssertions.assertEquals("file repositories are all constructed", log.fileCalls, RepositoryNames)
    ContractAssertions.assertEquals(
      "every file factory receives the selected root",
      log.fileRoots,
      RepositoryNames.map(_ => Paths.get(root.value))
    )

  private def fileModeLiveFactoriesConstructFileRepositories(): Unit =
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

  private final class FactoryCallLog:
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

    def recordFile(name: String, root: Path): Unit =
      fileNames = fileNames :+ name
      selectedFileRoots = selectedFileRoots :+ root

    def recordPostgres(name: String, settings: PostgresConnectionSettings): Unit =
      postgresNames = postgresNames :+ name
      selectedSettings = selectedSettings :+ settings

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

private[contract] object PasswordHasherContractTest:
  def run(): Unit =
    pbkdf2HashVerifiesPassword()
    pbkdf2HashUsesDifferentSalt()
    pbkdf2HasherVerifiesLegacySha256Hash()
    structuredHashIsDetected()
    malformedHashIsRejectedAndNeedsRehash()

  private def pbkdf2HashVerifiesPassword(): Unit =
    val hasher = Pbkdf2PasswordHasher(secureRandom = deterministicRandom())
    val password = PlainTextPassword.unsafe("correct horse battery staple")
    val hash = hasher.hash(password)

    ContractAssertions.assertEquals(
      "pbkdf2 verifies matching password",
      hasher.verify(password, hash),
      PasswordVerification.Verified
    )
    ContractAssertions.assertEquals(
      "pbkdf2 rejects wrong password",
      hasher.verify(PlainTextPassword.unsafe("wrong"), hash),
      PasswordVerification.Rejected
    )
    ContractAssertions.assertEquals("pbkdf2 hash does not need rehash", hasher.needsRehash(hash), false)

  private def pbkdf2HashUsesDifferentSalt(): Unit =
    val hasher = Pbkdf2PasswordHasher()
    val password = PlainTextPassword.unsafe("same-password")

    ContractAssertions.assertEquals("pbkdf2 hash is salted", hasher.hash(password).value == hasher.hash(password).value, false)

  private def pbkdf2HasherVerifiesLegacySha256Hash(): Unit =
    val password = PlainTextPassword.unsafe("legacy-password")
    val legacyHash = Sha256PasswordHasher().hash(password)
    val hasher = Pbkdf2PasswordHasher(secureRandom = deterministicRandom())

    ContractAssertions.assertEquals("legacy sha256 verifies", hasher.verify(password, legacyHash), PasswordVerification.Verified)
    ContractAssertions.assertEquals("legacy sha256 needs rehash", hasher.needsRehash(legacyHash), true)

  private def structuredHashIsDetected(): Unit =
    val hash = Pbkdf2PasswordHasher(secureRandom = deterministicRandom()).hash(PlainTextPassword.unsafe("password"))

    ContractAssertions.assertEquals("pbkdf2 structured hash is detected", Pbkdf2PasswordHasher.isStructuredHash(hash), true)
    ContractAssertions.assertEquals(
      "plain text is not a structured hash",
      Pbkdf2PasswordHasher.isStructuredHash(PasswordHash.unsafe("password")),
      false
    )

  private def malformedHashIsRejectedAndNeedsRehash(): Unit =
    val hasher = Pbkdf2PasswordHasher(secureRandom = deterministicRandom())
    val malformedHash = PasswordHash.unsafe("$pbkdf2-sha256$v1$bad-iterations$not-base64$also-not-base64")

    ContractAssertions.assertEquals(
      "malformed hash is rejected",
      hasher.verify(PlainTextPassword.unsafe("password"), malformedHash),
      PasswordVerification.Rejected
    )
    ContractAssertions.assertEquals("malformed hash needs rehash", hasher.needsRehash(malformedHash), true)
    ContractAssertions.assertEquals("malformed hash is not structured", Pbkdf2PasswordHasher.isStructuredHash(malformedHash), false)

  private def deterministicRandom(): SecureRandom =
    val random = SecureRandom.getInstance("SHA1PRNG")
    random.setSeed(7L)
    random

private[contract] object PostgresSupportContractTest:
  def run(): Unit =
    hikariConfigUsesTypedConnectionSettings()
    poolNameDoesNotExposeCredentials()
    connectionResourceClosesConnection()
    transactionCommitsAndRestoresAutoCommit()
    transactionRollsBackAndRestoresAutoCommit()
    transactionConnectionCommitsClosesAndRestoresAutoCommit()
    transactionConnectionRollsBackClosesAndRestoresAutoCommit()

  private def hikariConfigUsesTypedConnectionSettings(): Unit =
    val settings = postgresSettings("jdbc:postgresql://localhost:5432/slay_demo", "slay_user", "secret-password")
    val config = PostgresSupport.buildHikariConfig(settings)

    ContractAssertions.assertEquals("jdbc url is configured", config.getJdbcUrl, settings.jdbcUrl.value)
    ContractAssertions.assertEquals("database user is configured", config.getUsername, settings.user.map(_.value).orNull)
    ContractAssertions.assertEquals("database password is configured", config.getPassword, settings.password.map(_.value).orNull)
    ContractAssertions.assertEquals("pool size is bounded", config.getMaximumPoolSize, 10)

  private def poolNameDoesNotExposeCredentials(): Unit =
    val settings = postgresSettings("jdbc:postgresql://localhost:5432/slay_demo", "slay_user", "secret-password")
    val poolName = PostgresSupport.buildHikariConfig(settings).getPoolName

    ContractAssertions.assertEquals("pool name hides user", poolName.contains("slay_user"), false)
    ContractAssertions.assertEquals("pool name hides password", poolName.contains("secret-password"), false)

  private def connectionResourceClosesConnection(): Unit =
    val connection = RecordingConnection()

    PostgresSupport.connectionResource(IO.pure(connection.proxy)).use(_ => IO.unit).unsafeRunSync()

    ContractAssertions.assertEquals("resource closes connection", connection.calls, Vector("close"))

  private def transactionCommitsAndRestoresAutoCommit(): Unit =
    val connection = RecordingConnection()

    val result = PostgresSupport.withTransactionIO(connection.proxy)(IO.pure("created")).unsafeRunSync()

    ContractAssertions.assertEquals("transaction result", result, "created")
    ContractAssertions.assertEquals(
      "commit transaction lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "commit", "setAutoCommit(true)")
    )

  private def transactionRollsBackAndRestoresAutoCommit(): Unit =
    val connection = RecordingConnection()
    val error = RuntimeException("boom")

    try {
      PostgresSupport.withTransactionIO(connection.proxy)(IO.raiseError[String](error)).unsafeRunSync()
      assert(false, "expected transaction failure")
    } catch {
      case thrown: RuntimeException =>
        ContractAssertions.assertEquals("same transaction failure is rethrown", thrown, error)
    }

    ContractAssertions.assertEquals(
      "rollback transaction lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "rollback", "setAutoCommit(true)")
    )

  private def transactionConnectionCommitsClosesAndRestoresAutoCommit(): Unit =
    val connection = RecordingConnection()

    val result = PostgresSupport.withTransactionConnection(connection.proxy)(_ => "saved")

    ContractAssertions.assertEquals("transaction connection result", result, "saved")
    ContractAssertions.assertEquals(
      "commit transaction connection lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "commit", "setAutoCommit(true)", "close")
    )

  private def transactionConnectionRollsBackClosesAndRestoresAutoCommit(): Unit =
    val connection = RecordingConnection()
    val error = RuntimeException("boom")

    try {
      PostgresSupport.withTransactionConnection(connection.proxy)(_ => throw error)
      assert(false, "expected transaction connection failure")
    } catch {
      case thrown: RuntimeException =>
        ContractAssertions.assertEquals("same transaction connection failure is rethrown", thrown, error)
    }

    ContractAssertions.assertEquals(
      "rollback transaction connection lifecycle",
      connection.calls,
      Vector("getAutoCommit", "setAutoCommit(false)", "rollback", "setAutoCommit(true)", "close")
    )

  private def postgresSettings(
    jdbcUrl: String,
    user: String,
    password: String
  ): PostgresConnectionSettings =
    PostgresConnectionSettings(
      jdbcUrl = JdbcUrl(jdbcUrl),
      user = Some(DatabaseUser(user)),
      password = DatabasePassword.fromString(password)
    )

  private final class RecordingConnection(initialAutoCommit: Boolean = true):
    var autoCommit: Boolean = initialAutoCommit
    var calls: Vector[String] = Vector.empty

    val proxy: Connection =
      Proxy
        .newProxyInstance(
          classOf[Connection].getClassLoader,
          Array(classOf[Connection]),
          RecordingConnectionHandler(this)
        )
        .asInstanceOf[Connection]

  private final class RecordingConnectionHandler(connection: RecordingConnection) extends InvocationHandler:
    override def invoke(proxy: AnyRef, method: JavaMethod, args: Array[AnyRef]): AnyRef =
      method.getName match {
        case "getAutoCommit" =>
          connection.calls = connection.calls :+ "getAutoCommit"
          Boolean.box(connection.autoCommit)
        case "setAutoCommit" =>
          val nextAutoCommit = args(0).asInstanceOf[java.lang.Boolean].booleanValue()
          connection.autoCommit = nextAutoCommit
          connection.calls = connection.calls :+ s"setAutoCommit($nextAutoCommit)"
          null
        case "commit" =>
          connection.calls = connection.calls :+ "commit"
          null
        case "rollback" =>
          connection.calls = connection.calls :+ "rollback"
          null
        case "close" =>
          connection.calls = connection.calls :+ "close"
          null
        case "toString" =>
          "RecordingConnection"
        case "hashCode" =>
          Int.box(System.identityHashCode(proxy))
        case "equals" =>
          Boolean.box(proxy eq args(0))
        case _ =>
          defaultReturn(method.getReturnType)
      }

  private def defaultReturn(returnType: Class[?]): AnyRef =
    if returnType == java.lang.Boolean.TYPE then Boolean.box(false)
    else if returnType == java.lang.Byte.TYPE then Byte.box(0.toByte)
    else if returnType == java.lang.Short.TYPE then Short.box(0.toShort)
    else if returnType == java.lang.Integer.TYPE then Int.box(0)
    else if returnType == java.lang.Long.TYPE then Long.box(0L)
    else if returnType == java.lang.Float.TYPE then Float.box(0.0f)
    else if returnType == java.lang.Double.TYPE then Double.box(0.0d)
    else if returnType == java.lang.Character.TYPE then Char.box(0.toChar)
    else null

private[contract] object PostgresRepositoryBoundaryContractTest:
  private val SourceRoot: Path =
    Paths.get("src", "main", "scala", "services")

  private val ForbiddenRepositoryFragments: Vector[String] =
    Vector(
      "PostgresSupport.connect(",
      "PostgresSupport.withTransaction(",
      ".setAutoCommit(",
      ".commit(",
      ".rollback("
    )

  def run(): Unit =
    postgresRepositoriesUseSharedConnectionBoundaries()
    postgresRepositoryWritesUseTransactionBoundary()

  private def postgresRepositoriesUseSharedConnectionBoundaries(): Unit =
    assert(Files.exists(SourceRoot), s"source root does not exist: $SourceRoot")

    val repositories = postgresRepositoryFiles()
    assert(repositories.nonEmpty, s"no postgres repository files found under $SourceRoot")

    val violations = for {
      file <- repositories
      source = Files.readString(file)
      forbidden <- ForbiddenRepositoryFragments
      if source.contains(forbidden)
    } yield s"${SourceRoot.relativize(file)} contains forbidden fragment `$forbidden`"

    assert(
      violations.isEmpty,
      s"Postgres repositories must use PostgresSupport shared connection/transaction helpers:\n${violations.mkString("\n")}"
    )

  private def postgresRepositoryWritesUseTransactionBoundary(): Unit =
    val violations = for {
      file <- postgresRepositoryFiles()
      lines = Files.readString(file).linesIterator.toVector
      (line, index) <- lines.zipWithIndex
      if isRepositoryWriteLine(line)
      boundary = nearestConnectionBoundary(lines, index)
      if !boundary.exists(_.contains("PostgresSupport.withTransactionConnection(settings)"))
    } yield {
      val renderedBoundary = boundary.getOrElse("<no connection boundary>")
      s"${SourceRoot.relativize(file)}:${index + 1} writes outside transaction boundary; nearest boundary: $renderedBoundary"
    }

    assert(
      violations.isEmpty,
      s"Postgres repository writes must use PostgresSupport.withTransactionConnection:\n${violations.mkString("\n")}"
    )

  private def isRepositoryWriteLine(line: String): Boolean =
    val normalized = line.trim
    normalized.contains("executeUpdate(") ||
      normalized.contains("\"\"\"INSERT INTO ") ||
      normalized.contains("|INSERT INTO ") ||
      normalized.contains("\"\"\"UPDATE ") ||
      normalized.contains("|UPDATE ")

  private def nearestConnectionBoundary(lines: Vector[String], index: Int): Option[String] =
    lines
      .take(index + 1)
      .reverse
      .find(line =>
        line.contains("PostgresSupport.withTransactionConnection(settings)") ||
          line.contains("PostgresSupport.withConnection(settings)")
      )
      .map(_.trim)

  private def postgresRepositoryFiles(): Vector[Path] =
    val stream = Files.walk(SourceRoot)
    try {
      stream
        .iterator()
        .asScala
        .toVector
        .filter(path => Files.isRegularFile(path))
        .filter(path => path.getFileName.toString.endsWith("Repository.scala"))
        .filter(path => path.getFileName.toString.contains("Postgres"))
        .sortBy(_.toString)
    } finally {
      stream.close()
    }
