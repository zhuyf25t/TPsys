package slaydemo.backend

import slaydemo.backend.identity.database.{IdentityAccountCreateResult, IdentityAccountRepository, InMemoryIdentityAccountRepository}
import slaydemo.backend.identity.objects.{AccountStatus, DisplayName, IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.identity.ports.{IdentityIdGenerator, SessionTokenGenerator, Sha256PasswordHasher}
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
}
