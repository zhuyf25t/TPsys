package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Header, Headers, Method, Request}
import org.typelevel.ci.CIString

import slaydemo.backend.identity.api.IdentityAccountSummary
import slaydemo.backend.identity.objects.{IdentityAccount, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
  IdentitySessionError
}
import slaydemo.backend.shared.objects.UserId

object IdentityHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    registerParsesCommandAndRendersAuth()
    registerValidationAndServiceErrors()
    sessionParsesCommandAndMapsInvalidCredentials()
    currentSessionParsesHeadersAndMapsErrors()
    accountsRendersActiveSummaries()

    println("Identity http4s contract checks passed")
  }

  private def registerParsesCommandAndRendersAuth(): Unit = {
    val service = RecordingIdentityService()
    val response = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/api/identity/register")
        .withEntity("""{"handle":"Alice","password":"safe-pass","skinId":"soldier"}""")
    )

    assertEquals("register status", response.status, 200)
    assertContains("register handle", response.body, """"handle":"Alice"""")
    assertContains("register skin", response.body, """"skinId":"soldier"""")
    assertContains("register session", response.body, """"session":"session-alice"""")
    assertEquals("register command count", service.registerCommands.length, 1)
    assertEquals("register command handle", service.registerCommands.head.handle, PlayerHandle("Alice"))
    assertEquals("register command password", service.registerCommands.head.password.value, "safe-pass")
    assertEquals("register command skin", service.registerCommands.head.skinId, SkinId.Soldier)
  }

  private def registerValidationAndServiceErrors(): Unit = {
    val service = RecordingIdentityService()
    val invalidHandle = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/identity/register")
        .withEntity("""{"handle":"ab","password":"safe-pass","skinId":"blue"}""")
    )
    val invalidSkin = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/identity/register")
        .withEntity("""{"handle":"Alice","password":"safe-pass","skinId":"purple"}""")
    )

    assertEquals("invalid handle status", invalidHandle.status, 400)
    assertContains("invalid handle code", invalidHandle.body, """"code":"invalid_handle"""")
    assertEquals("invalid skin status", invalidSkin.status, 400)
    assertContains("invalid skin code", invalidSkin.body, """"code":"invalid_skin"""")
    assertEquals("invalid register requests do not call service", service.registerCommands, Vector.empty)

    service.registerResults = Vector(Left(IdentityRegistrationError.HandleTaken))
    val handleTaken = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/identity/register")
        .withEntity("""{"handle":"Alice","password":"safe-pass","skinId":"blue"}""")
    )

    assertEquals("handle taken status", handleTaken.status, 409)
    assertContains("handle taken code", handleTaken.body, """"code":"handle_taken"""")
    assertEquals("handle taken calls service", service.registerCommands.length, 1)
  }

  private def sessionParsesCommandAndMapsInvalidCredentials(): Unit = {
    val service = RecordingIdentityService()
    val issued = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/identity/session")
        .withEntity("""{"handle":"Alice","password":"safe-pass"}""")
    )

    assertEquals("session status", issued.status, 200)
    assertContains("session handle", issued.body, """"handle":"Alice"""")
    assertContains("session token", issued.body, """"session":"session-alice"""")
    assertEquals("session command count", service.sessionCommands.length, 1)

    service.sessionResults = Vector(Left(IdentitySessionError.InvalidCredentials))
    val invalidCredentials = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/identity/session")
        .withEntity("""{"handle":"Alice","password":"wrong-pass"}""")
    )

    assertEquals("invalid credentials status", invalidCredentials.status, 401)
    assertContains("invalid credentials code", invalidCredentials.body, """"code":"invalid_credentials"""")
    assertEquals("invalid credentials calls service", service.sessionCommands.length, 2)
  }

  private def currentSessionParsesHeadersAndMapsErrors(): Unit = {
    val service = RecordingIdentityService()
    val missing = run(service, Request[IO](method = Method.GET, uri = uri"/identity/me"))

    assertEquals("missing session status", missing.status, 401)
    assertContains("missing session code", missing.body, """"code":"missing_session"""")
    assertEquals("missing current call", service.currentCalls, Vector(None))

    val bearer = run(
      service,
      Request[IO](method = Method.GET, uri = uri"/api/identity/me", headers = headers("Authorization", "Bearer session-alice"))
    )
    val tokenHeader = run(
      service,
      Request[IO](method = Method.GET, uri = uri"/identity/me", headers = headers("X-Session-Token", "session-bob"))
    )

    assertEquals("bearer current status", bearer.status, 200)
    assertContains("bearer current session", bearer.body, """"session":"session-alice"""")
    assertEquals("x session current status", tokenHeader.status, 200)
    assertContains("x session current session", tokenHeader.body, """"session":"session-bob"""")
    assertEquals(
      "current header calls",
      service.currentCalls,
      Vector(None, Some(SessionToken("session-alice")), Some(SessionToken("session-bob")))
    )

    service.currentResults = Vector(Left(IdentityCurrentSessionError.InvalidSession))
    val invalid = run(
      service,
      Request[IO](method = Method.GET, uri = uri"/identity/me", headers = headers("Authorization", "session-missing"))
    )

    assertEquals("invalid current status", invalid.status, 401)
    assertContains("invalid current code", invalid.body, """"code":"invalid_session"""")
  }

  private def accountsRendersActiveSummaries(): Unit = {
    val service = RecordingIdentityService()
    service.accountSummaries = Vector(
      IdentityAccountSummary("admin", "admin", "blue"),
      IdentityAccountSummary("Alice", "Alice", "survivor")
    )
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/identity/accounts"))

    assertEquals("accounts status", response.status, 200)
    assertContains("accounts wrapper", response.body, """"accounts":[""")
    assertContains("accounts admin", response.body, """"handle":"admin"""")
    assertContains("accounts skin", response.body, """"skinId":"survivor"""")
    assertEquals("accounts list called", service.listActiveAccountsCalls, 1)
  }

  private def run(service: IdentityService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.identityRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private def headers(name: String, value: String): Headers =
    Headers(Header.Raw(CIString(name), value))

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingIdentityService extends IdentityService {
    var registerResults: Vector[Either[IdentityRegistrationError, IdentityAccount]] = Vector.empty
    var sessionResults: Vector[Either[IdentitySessionError, IdentityAccount]] = Vector.empty
    var currentResults: Vector[Either[IdentityCurrentSessionError, IdentityAccount]] = Vector.empty
    var accountSummaries: Vector[IdentityAccountSummary] = Vector.empty
    var registerCommands: Vector[IdentityRegistrationCommand] = Vector.empty
    var sessionCommands: Vector[IdentitySessionCommand] = Vector.empty
    var currentCalls: Vector[Option[SessionToken]] = Vector.empty
    var listActiveAccountsCalls: Int = 0

    override def register(command: IdentityRegistrationCommand): Either[IdentityRegistrationError, IdentityAccount] = {
      registerCommands = registerCommands :+ command
      takeResult(
        registerResults,
        remaining => registerResults = remaining,
        Right(account(command.handle, command.skinId, Some(SessionToken(s"session-${command.handle.key}"))))
      )
    }

    override def issueSession(command: IdentitySessionCommand): Either[IdentitySessionError, IdentityAccount] = {
      sessionCommands = sessionCommands :+ command
      takeResult(
        sessionResults,
        remaining => sessionResults = remaining,
        Right(account(command.handle, SkinId.Blue, Some(SessionToken(s"session-${command.handle.key}"))))
      )
    }

    override def current(sessionToken: Option[SessionToken]): Either[IdentityCurrentSessionError, IdentityAccount] = {
      currentCalls = currentCalls :+ sessionToken
      takeResult(
        currentResults,
        remaining => currentResults = remaining,
        sessionToken match {
          case None =>
            Left(IdentityCurrentSessionError.MissingSession)
          case Some(token) =>
            Right(account(PlayerHandle(token.value.stripPrefix("session-").capitalize), SkinId.Blue, Some(token)))
        }
      )
    }

    override def listActiveAccounts(): Vector[IdentityAccountSummary] = {
      listActiveAccountsCalls += 1
      accountSummaries
    }

    private def takeResult[E, A](
      results: Vector[Either[E, A]],
      saveRemaining: Vector[Either[E, A]] => Unit,
      default: Either[E, A]
    ): Either[E, A] =
      results match {
        case head +: tail =>
          saveRemaining(tail)
          head
        case _ =>
          default
      }
  }

  private def account(
    handle: PlayerHandle,
    skinId: SkinId,
    sessionToken: Option[SessionToken]
  ): IdentityAccount =
    IdentityAccount.active(
      userId = UserId(s"user-${handle.key}"),
      handle = handle,
      skinId = skinId,
      sessionToken = sessionToken
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
