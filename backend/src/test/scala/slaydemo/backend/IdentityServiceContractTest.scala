package slaydemo.backend

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import slaydemo.backend.identity.database.{
  FileIdentityAccountRepository,
  IdentityAccountCreateResult,
  IdentityAccountRepository,
  InMemoryIdentityAccountRepository
}
import slaydemo.backend.identity.objects.{AccountStatus, DisplayName, IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.identity.ports.{IdentityIdGenerator, Pbkdf2PasswordHasher, SessionTokenGenerator, Sha256PasswordHasher}
import slaydemo.backend.identity.services.{
  DefaultIdentityService,
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentitySessionCommand,
  IdentitySessionError
}
import slaydemo.backend.shared.objects.UserId

object IdentityServiceContractTest {
  def main(args: Array[String]): Unit = {
    val identity = service()

    val alice = register(identity, "Alice", "password", SkinId.Survivor)
    duplicateHandleIsRejected(identity)
    builtinAdminHandleIsReserved(identity)
    sessionIssuanceAndCurrentSession(identity, alice)
    builtinAdminSession(identity)
    activeAccountSummariesIncludeAdmin(identity)
    legacyPlaintextPasswordLoginUpgradesToHash()
    sha256PasswordLoginUpgradesToPbkdf2()
    fileRepositoryPersistsHashAndSessions()
    fileRepositoryUpgradesLegacyPlaintext()

    println("Identity service contract checks passed")
  }

  private def duplicateHandleIsRejected(identity: DefaultIdentityService): Unit =
    assertEquals(
      "duplicate handle is rejected case-insensitively",
      identity.register(registrationCommand("alice", "password", SkinId.Blue)),
      Left(IdentityRegistrationError.HandleTaken)
    )

  private def builtinAdminHandleIsReserved(identity: DefaultIdentityService): Unit =
    assertEquals(
      "builtin admin handle cannot be registered",
      identity.register(registrationCommand("admin", "password", SkinId.Blue)),
      Left(IdentityRegistrationError.HandleTaken)
    )

  private def sessionIssuanceAndCurrentSession(
    identity: DefaultIdentityService,
    alice: IdentityAccount
  ): Unit = {
    assertEquals(
      "missing session is explicit",
      identity.current(None),
      Left(IdentityCurrentSessionError.MissingSession)
    )
    assertEquals(
      "invalid session is explicit",
      identity.current(Some(SessionToken("session-missing"))),
      Left(IdentityCurrentSessionError.InvalidSession)
    )
    assertEquals(
      "bad password is rejected",
      identity.issueSession(IdentitySessionCommand(alice.handle, PlainTextPassword.unsafe("wrong-password"))),
      Left(IdentitySessionError.InvalidCredentials)
    )

    val sessionAccount = identity
      .issueSession(IdentitySessionCommand(alice.handle, PlainTextPassword.unsafe("password")))
      .fold(error => fail(s"issue session failed: $error"), identity => identity)
    val sessionToken = sessionAccount.sessionToken.getOrElse(fail("missing issued session"))

    assertEquals("current session resolves account", identity.current(Some(sessionToken)), Right(sessionAccount))
  }

  private def builtinAdminSession(identity: DefaultIdentityService): Unit = {
    val badAdmin = identity.issueSession(IdentitySessionCommand(PlayerHandle("admin"), PlainTextPassword.unsafe("wrong-password")))
    val admin = identity
      .issueSession(IdentitySessionCommand(PlayerHandle("admin"), PlainTextPassword.unsafe("admin123456")))
      .fold(error => fail(s"admin session failed: $error"), identity => identity)

    assertEquals("bad admin password is rejected", badAdmin, Left(IdentitySessionError.InvalidCredentials))
    assertEquals("admin handle is stable", admin.handle, PlayerHandle("admin"))
    assertEquals(
      "admin session resolves through current",
      identity.current(admin.sessionToken),
      Right(admin)
    )
  }

  private def activeAccountSummariesIncludeAdmin(identity: DefaultIdentityService): Unit =
    assertEquals(
      "active account summaries include playable accounts and builtin admin",
      identity.listActiveAccounts().map(_.handle),
      Vector("admin", "Alice")
    )

  private def legacyPlaintextPasswordLoginUpgradesToHash(): Unit = {
    val repository = LegacyPlaintextIdentityRepository("LegacyUser", "legacy-pass")
    val identity = DefaultIdentityService(
      repository = repository,
      identityIdGenerator = DeterministicIdentityIdGenerator,
      sessionTokenGenerator = DeterministicSessionTokenGenerator,
      passwordHasher = Sha256PasswordHasher()
    )
    val handle = PlayerHandle("LegacyUser")

    assertEquals(
      "legacy plaintext wrong password is rejected",
      identity.issueSession(IdentitySessionCommand(handle, PlainTextPassword.unsafe("wrong-password"))),
      Left(IdentitySessionError.InvalidCredentials)
    )
    assertEquals("wrong password does not upgrade legacy row", repository.upgradeCount, 0)

    val first = identity
      .issueSession(IdentitySessionCommand(handle, PlainTextPassword.unsafe("legacy-pass")))
      .fold(error => fail(s"legacy plaintext session failed: $error"), identity => identity)

    assertEquals("legacy plaintext login returns account", first.handle, handle)
    assertEquals("legacy plaintext login upgrades password hash", repository.upgradeCount, 1)
    assertEquals("legacy plaintext checked once", repository.legacyAuthenticationCount, 2)
    val expectedHash = Sha256PasswordHasher().hash(PlainTextPassword.unsafe("legacy-pass"))
    assertEquals("stored password is now current hash", repository.storedHash.exists(_.value == expectedHash.value), true)

    identity
      .issueSession(IdentitySessionCommand(handle, PlainTextPassword.unsafe("legacy-pass")))
      .fold(error => fail(s"upgraded hash session failed: $error"), identity => identity)

    assertEquals("upgraded account authenticates through hash without another legacy check", repository.legacyAuthenticationCount, 2)
  }

  private def sha256PasswordLoginUpgradesToPbkdf2(): Unit = {
    val repository = new InMemoryIdentityAccountRepository()
    val handle = PlayerHandle("ShaUser")
    val password = PlainTextPassword.unsafe("sha-password")
    val legacyHash = Sha256PasswordHasher().hash(password)
    val account = IdentityAccount.active(
      userId = UserId("sha-user-id"),
      handle = handle,
      skinId = SkinId.Blue,
      sessionToken = None
    )
    val identity = DefaultIdentityService(
      repository = repository,
      identityIdGenerator = DeterministicIdentityIdGenerator,
      sessionTokenGenerator = DeterministicSessionTokenGenerator,
      passwordHasher = Pbkdf2PasswordHasher()
    )

    assertEquals(
      "sha user is inserted",
      repository.create(account, legacyHash),
      IdentityAccountCreateResult.Created(account)
    )

    identity
      .issueSession(IdentitySessionCommand(handle, password))
      .fold(error => fail(s"sha session failed: $error"), identity => identity)

    val upgradedHash = repository.findPasswordHashByHandle(handle).getOrElse(fail("missing upgraded hash"))
    assertEquals("sha hash is upgraded to pbkdf2", Pbkdf2PasswordHasher.isStructuredHash(upgradedHash), true)
    assertEquals("upgraded hash differs from legacy sha256", upgradedHash.value == legacyHash.value, false)
  }

  private def fileRepositoryPersistsHashAndSessions(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-identity-file-contract")
    try {
      val storagePath = directory.resolve("identity-accounts.json")
      val repository = FileIdentityAccountRepository(storagePath)
      val passwordHash = Sha256PasswordHasher().hash(PlainTextPassword.unsafe("file-pass"))
      val account = IdentityAccount.active(
        userId = UserId("file-user"),
        handle = PlayerHandle("FileUser"),
        skinId = SkinId.Soldier,
        sessionToken = None
      ).copy(displayName = DisplayName("File User"))

      assertEquals(
        "file identity create stores account",
        repository.create(account, passwordHash),
        IdentityAccountCreateResult.Created(account)
      )

      val storedAfterCreate = Files.readString(storagePath, StandardCharsets.UTF_8)
      assertEquals(
        "file identity schema marker is stable",
        storedAfterCreate.contains(""""schema": "slay-demo.identity-accounts.v1""""),
        true
      )
      Vector("userId", "handle", "displayName", "skinId", "sessionToken", "active", "password").foreach { field =>
        assertEquals(s"file identity persisted field is present: $field", storedAfterCreate.contains(s""""$field""""), true)
      }
      assertEquals(
        "file identity empty session token is persisted as empty string",
        storedAfterCreate.contains(""""sessionToken": """""),
        true
      )
      assertEquals(
        "file identity password hash is persisted",
        storedAfterCreate.contains(s""""password": "${passwordHash.value}""""),
        true
      )
      assertEquals("file identity raw plaintext is not persisted for hash row", storedAfterCreate.contains("file-pass"), false)
      val reloadedWithoutSession = FileIdentityAccountRepository(storagePath)
      assertEquals(
        "file identity empty session token reloads as no session",
        reloadedWithoutSession.findByHandle(PlayerHandle("fileuser")).flatMap(_.sessionToken),
        None
      )

      assertEquals(
        "file identity duplicate handle is case-insensitive",
        repository.create(account.copy(handle = PlayerHandle("fileuser")), passwordHash),
        IdentityAccountCreateResult.HandleAlreadyExists(account)
      )
      assertEquals(
        "file identity hash authentication works",
        repository.authenticate(PlayerHandle("fileuser"), passwordHash),
        Some(account)
      )
      assertEquals(
        "file identity plaintext does not authenticate as legacy for hash row",
        repository.authenticateLegacyPlaintext(PlayerHandle("FileUser"), PlainTextPassword.unsafe("file-pass")),
        None
      )

      val sessionAccount = repository
        .updateSession(PlayerHandle("fileuser"), SessionToken("session-file-user"))
        .getOrElse(fail("file identity session update failed"))

      val reloaded = FileIdentityAccountRepository(storagePath)
      assertEquals("file identity reload finds handle", reloaded.findByHandle(PlayerHandle("FILEUSER")), Some(sessionAccount))
      assertEquals(
        "file identity reload finds session token",
        reloaded.findBySessionToken(SessionToken("session-file-user")),
        Some(sessionAccount)
      )
      assertEquals("file identity reload lists active accounts", reloaded.listActiveAccounts().map(_.handle), Vector(PlayerHandle("FileUser")))
    } finally {
      deleteRecursively(directory)
    }
  }

  private def fileRepositoryUpgradesLegacyPlaintext(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-identity-legacy-file-contract")
    try {
      val storagePath = directory.resolve("identity-accounts.json")
      Files.writeString(
        storagePath,
        """{
          |  "schema": "slay-demo.identity-accounts.v1",
          |  "accounts": [
          |    {
          |      "userId": "legacy-file-user",
          |      "handle": "LegacyFile",
          |      "displayName": "Legacy File",
          |      "skinId": "blue",
          |      "sessionToken": "",
          |      "active": true,
          |      "password": "legacy-pass"
          |    }
          |  ]
          |}
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val repository = FileIdentityAccountRepository(storagePath)
      val identity = DefaultIdentityService(
        repository = repository,
        identityIdGenerator = DeterministicIdentityIdGenerator,
        sessionTokenGenerator = DeterministicSessionTokenGenerator,
        passwordHasher = Sha256PasswordHasher()
      )
      val handle = PlayerHandle("LegacyFile")

      identity
        .issueSession(IdentitySessionCommand(handle, PlainTextPassword.unsafe("legacy-pass")))
        .fold(error => fail(s"file legacy plaintext session failed: $error"), identity => identity)

      val expectedHash = Sha256PasswordHasher().hash(PlainTextPassword.unsafe("legacy-pass"))
      val reloaded = FileIdentityAccountRepository(storagePath)
      assertEquals(
        "file legacy plaintext is no longer accepted after upgrade",
        reloaded.authenticateLegacyPlaintext(handle, PlainTextPassword.unsafe("legacy-pass")),
        None
      )
      assertEquals(
        "file legacy plaintext upgrade stores current hash",
        reloaded.authenticate(handle, expectedHash).exists(_.handle == handle),
        true
      )
      assertEquals("file legacy plaintext secret removed", Files.readString(storagePath).contains("\"legacy-pass\""), false)
    } finally {
      deleteRecursively(directory)
    }
  }

  private def register(
    identity: DefaultIdentityService,
    handle: String,
    password: String,
    skinId: SkinId
  ): IdentityAccount =
    identity
      .register(registrationCommand(handle, password, skinId))
      .fold(error => fail(s"registration failed: $error"), identity => identity)

  private def registrationCommand(
    handle: String,
    password: String,
    skinId: SkinId
  ): IdentityRegistrationCommand =
    IdentityRegistrationCommand(
      handle = PlayerHandle.forRegistration(handle).getOrElse(fail(s"invalid test handle $handle")),
      password = PlainTextPassword.unsafe(password),
      skinId = skinId
    )

  private def service(): DefaultIdentityService =
    DefaultIdentityService(
      repository = new InMemoryIdentityAccountRepository(),
      identityIdGenerator = DeterministicIdentityIdGenerator,
      sessionTokenGenerator = DeterministicSessionTokenGenerator,
      passwordHasher = Sha256PasswordHasher()
    )

  private object DeterministicIdentityIdGenerator extends IdentityIdGenerator {
    private var nextNumber: Long = 1L

    override def nextUserId(): UserId = {
      val id = UserId(s"user-$nextNumber")
      nextNumber += 1L
      id
    }
  }

  private object DeterministicSessionTokenGenerator extends SessionTokenGenerator {
    private var nextNumber: Long = 1L

    override def nextSessionToken(handle: PlayerHandle): SessionToken = {
      val token = SessionToken(s"session-${handle.key}-$nextNumber")
      nextNumber += 1L
      token
    }
  }

  private final class LegacyPlaintextIdentityRepository(
    initialHandle: String,
    legacyPassword: String
  ) extends IdentityAccountRepository {
    private val handle = PlayerHandle(initialHandle)
    private var account = IdentityAccount(
      userId = UserId("legacy-user-id"),
      handle = handle,
      displayName = DisplayName(initialHandle),
      skinId = SkinId.Blue,
      sessionToken = None,
      status = AccountStatus.Active
    )
    private var passwordHash: Option[PasswordHash] = None
    private var legacyAuthenticationCounter: Int = 0
    private var upgradeCounter: Int = 0

    def storedHash: Option[PasswordHash] =
      passwordHash

    def legacyAuthenticationCount: Int =
      legacyAuthenticationCounter

    def upgradeCount: Int =
      upgradeCounter

    override def findByHandle(handle: PlayerHandle): Option[IdentityAccount] =
      Option.when(handle.key == this.handle.key)(account)

    override def findPasswordHashByHandle(handle: PlayerHandle): Option[PasswordHash] =
      if handle.key == this.handle.key then passwordHash else None

    override def findBySessionToken(sessionToken: SessionToken): Option[IdentityAccount] =
      Option.when(account.sessionToken.contains(sessionToken))(account)

    override def authenticate(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
      Option.when(handle.key == this.handle.key && this.passwordHash.exists(_.value == passwordHash.value))(account)

    override def authenticateLegacyPlaintext(handle: PlayerHandle, password: PlainTextPassword): Option[IdentityAccount] = {
      legacyAuthenticationCounter += 1
      Option.when(handle.key == this.handle.key && passwordHash.isEmpty && password.value == legacyPassword)(account)
    }

    override def create(account: IdentityAccount, passwordHash: PasswordHash): IdentityAccountCreateResult =
      IdentityAccountCreateResult.HandleAlreadyExists(this.account)

    override def replacePasswordHash(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount] =
      Option.when(handle.key == this.handle.key) {
        this.passwordHash = Some(passwordHash)
        upgradeCounter += 1
        account
      }

    override def updateSession(handle: PlayerHandle, sessionToken: SessionToken): Option[IdentityAccount] =
      Option.when(handle.key == this.handle.key) {
        account = account.copy(sessionToken = Some(sessionToken))
        account
      }

    override def listActiveAccounts(): Vector[IdentityAccount] =
      Vector(account)
  }

  private object LegacyPlaintextIdentityRepository {
    def apply(handle: String, legacyPassword: String): LegacyPlaintextIdentityRepository =
      new LegacyPlaintextIdentityRepository(handle, legacyPassword)
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)

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
}
