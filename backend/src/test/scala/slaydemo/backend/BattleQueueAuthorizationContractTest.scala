package slaydemo.backend

import slaydemo.backend.battle.objects.{QueueRequestId, Rating}
import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinCommand,
  DefaultBattleQueueJoinAuthorizationService
}
import slaydemo.backend.identity.database.InMemoryIdentityAccountRepository
import slaydemo.backend.identity.objects.{IdentityAccount, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.identity.ports.{IdentityIdGenerator, SessionTokenGenerator, Sha256PasswordHasher}
import slaydemo.backend.identity.services.{DefaultIdentityService, IdentityRegistrationCommand}
import slaydemo.backend.shared.objects.UserId

object BattleQueueAuthorizationContractTest {
  def main(args: Array[String]): Unit = {
    val identity = DefaultIdentityService(
      repository = new InMemoryIdentityAccountRepository(),
      identityIdGenerator = DeterministicIdentityIdGenerator,
      sessionTokenGenerator = DeterministicSessionTokenGenerator,
      passwordHasher = Sha256PasswordHasher()
    )
    val authorization = DefaultBattleQueueJoinAuthorizationService(identity)
    val alice = registered(identity, "Alice")
    val bob = registered(identity, "Bob")

    assertEquals(
      "missing session is invalid",
      authorization.authorize(joinCommand(alice.handle, SessionToken("session-missing"))),
      Left(BattleQueueJoinAuthorizationError.InvalidSession)
    )
    assertEquals(
      "handle/session mismatch is forbidden",
      authorization.authorize(joinCommand(bob.handle, alice.sessionToken.getOrElse(fail("missing alice session")))),
      Left(BattleQueueJoinAuthorizationError.HandleMismatch)
    )
    assertEquals(
      "valid session authorizes matching handle",
      authorization.authorize(joinCommand(alice.handle, alice.sessionToken.getOrElse(fail("missing alice session")))),
      Right(())
    )

    println("BattleQueue authorization contract checks passed")
  }

  private def registered(identity: DefaultIdentityService, handle: String): IdentityAccount =
    identity
      .register(
        IdentityRegistrationCommand(
          handle = PlayerHandle.forRegistration(handle).getOrElse(fail(s"invalid handle $handle")),
          password = PlainTextPassword.unsafe("password"),
          skinId = SkinId.Blue
        )
      )
      .fold(error => fail(s"registration failed: $error"), identity => identity)

  private def joinCommand(handle: PlayerHandle, sessionToken: SessionToken): BattleQueueJoinCommand =
    BattleQueueJoinCommand(
      handle = handle,
      sessionToken = sessionToken,
      queueRequestId = Some(QueueRequestId(s"queue-${handle.key}")),
      rating = Some(Rating(1200)),
      avatar = Some("avatar"),
      skin = Some("blue")
    )

  private object DeterministicIdentityIdGenerator extends IdentityIdGenerator {
    override def nextUserId(): UserId =
      UserId("user-fixed")
  }

  private object DeterministicSessionTokenGenerator extends SessionTokenGenerator {
    override def nextSessionToken(handle: PlayerHandle): SessionToken =
      SessionToken(s"session-${handle.key}")
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
