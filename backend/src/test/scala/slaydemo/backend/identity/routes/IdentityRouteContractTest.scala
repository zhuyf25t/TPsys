package slaydemo.backend.identity.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.identity.api.IdentityAccountSummary
import slaydemo.backend.identity.objects.{IdentityAccount, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
  IdentitySessionError
}
import slaydemo.backend.shared.objects.UserId

object IdentityRouteContractTest {
  def main(args: Array[String]): Unit = {
    registerParsesCommandAndRendersAuth()
    registerValidationAndServiceErrors()
    sessionParsesCommandAndMapsInvalidCredentials()
    currentSessionParsesHeadersAndMapsErrors()
    accountsRendersActiveSummaries()

    println("Identity route contract checks passed")
  }

  private def registerParsesCommandAndRendersAuth(): Unit = {
    val service = RecordingIdentityService()

    withIdentityServer(service) { uri =>
      val response = postJson(
        uri.resolve("/identity/register"),
        """{"handle":"Alice","password":"safe-pass","skinId":"soldier"}"""
      )

      assertEquals("register status", response.status, 200)
      assertContains("register handle", response.body, """"handle":"Alice"""")
      assertContains("register skin", response.body, """"skinId":"soldier"""")
      assertContains("register session", response.body, """"session":"session-alice"""")
      assertEquals("register command count", service.registerCommands.length, 1)
      val command = service.registerCommands.head
      assertEquals("register command handle", command.handle, PlayerHandle("Alice"))
      assertEquals("register command password", command.password.value, "safe-pass")
      assertEquals("register command skin", command.skinId, SkinId.Soldier)
    }
  }

  private def registerValidationAndServiceErrors(): Unit = {
    val service = RecordingIdentityService()

    withIdentityServer(service) { uri =>
      val invalidHandle = postJson(
        uri.resolve("/identity/register"),
        """{"handle":"ab","password":"safe-pass","skinId":"blue"}"""
      )
      val invalidPassword = postJson(
        uri.resolve("/identity/register"),
        """{"handle":"Alice","password":"123","skinId":"blue"}"""
      )
      val invalidSkin = postJson(
        uri.resolve("/identity/register"),
        """{"handle":"Alice","password":"safe-pass","skinId":"purple"}"""
      )

      assertEquals("invalid handle status", invalidHandle.status, 400)
      assertContains("invalid handle code", invalidHandle.body, """"code":"invalid_handle"""")
      assertEquals("invalid password status", invalidPassword.status, 400)
      assertContains("invalid password code", invalidPassword.body, """"code":"invalid_password"""")
      assertEquals("invalid skin status", invalidSkin.status, 400)
      assertContains("invalid skin code", invalidSkin.body, """"code":"invalid_skin"""")
      assertEquals("invalid register requests do not call service", service.registerCommands, Vector.empty)

      service.registerResults = Vector(Left(IdentityRegistrationError.HandleTaken))
      val handleTaken = postJson(
        uri.resolve("/identity/register"),
        """{"handle":"Alice","password":"safe-pass","skinId":"blue"}"""
      )

      assertEquals("handle taken status", handleTaken.status, 409)
      assertContains("handle taken code", handleTaken.body, """"code":"handle_taken"""")
      assertEquals("handle taken calls service", service.registerCommands.length, 1)
    }
  }

  private def sessionParsesCommandAndMapsInvalidCredentials(): Unit = {
    val service = RecordingIdentityService()

    withIdentityServer(service) { uri =>
      val issued = postJson(
        uri.resolve("/identity/session"),
        """{"handle":"Alice","password":"safe-pass"}"""
      )

      assertEquals("session status", issued.status, 200)
      assertContains("session handle", issued.body, """"handle":"Alice"""")
      assertContains("session token", issued.body, """"session":"session-alice"""")
      assertEquals("session command count", service.sessionCommands.length, 1)
      assertEquals("session command handle", service.sessionCommands.head.handle, PlayerHandle("Alice"))
      assertEquals("session command password", service.sessionCommands.head.password.value, "safe-pass")

      service.sessionResults = Vector(Left(IdentitySessionError.InvalidCredentials))
      val invalidCredentials = postJson(
        uri.resolve("/identity/session"),
        """{"handle":"Alice","password":"wrong-pass"}"""
      )

      assertEquals("invalid credentials status", invalidCredentials.status, 401)
      assertContains("invalid credentials code", invalidCredentials.body, """"code":"invalid_credentials"""")
      assertEquals("invalid credentials calls service", service.sessionCommands.length, 2)
    }
  }

  private def currentSessionParsesHeadersAndMapsErrors(): Unit = {
    val service = RecordingIdentityService()

    withIdentityServer(service) { uri =>
      val missing = get(uri.resolve("/identity/me"))

      assertEquals("missing session status", missing.status, 401)
      assertContains("missing session code", missing.body, """"code":"missing_session"""")
      assertEquals("missing current call", service.currentCalls, Vector(None))

      val bearer = get(uri.resolve("/identity/me"), authorization = Some("Bearer session-alice"))
      val header = get(uri.resolve("/identity/me"), sessionToken = Some("session-bob"))

      assertEquals("bearer current status", bearer.status, 200)
      assertContains("bearer current session", bearer.body, """"session":"session-alice"""")
      assertEquals("x session current status", header.status, 200)
      assertContains("x session current session", header.body, """"session":"session-bob"""")
      assertEquals(
        "current header calls",
        service.currentCalls,
        Vector(None, Some(SessionToken("session-alice")), Some(SessionToken("session-bob")))
      )

      service.currentResults = Vector(Left(IdentityCurrentSessionError.InvalidSession))
      val invalid = get(uri.resolve("/identity/me"), authorization = Some("session-missing"))

      assertEquals("invalid current status", invalid.status, 401)
      assertContains("invalid current code", invalid.body, """"code":"invalid_session"""")
    }
  }

  private def accountsRendersActiveSummaries(): Unit = {
    val service = RecordingIdentityService()
    service.accountSummaries = Vector(
      IdentityAccountSummary("admin", "admin", "blue"),
      IdentityAccountSummary("Alice", "Alice", "survivor")
    )

    withIdentityServer(service) { uri =>
      val response = get(uri.resolve("/identity/accounts"))

      assertEquals("accounts status", response.status, 200)
      assertContains("accounts wrapper", response.body, """"accounts":[""")
      assertContains("accounts admin", response.body, """"handle":"admin"""")
      assertContains("accounts skin", response.body, """"skinId":"survivor"""")
      assertEquals("accounts list called", service.listActiveAccountsCalls, 1)
    }
  }

  private def withIdentityServer[A](service: RecordingIdentityService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = IdentityRoutes(service)
    server.createContext("/identity/register", exchange => routes.register(exchange))
    server.createContext("/identity/session", exchange => routes.issueSession(exchange))
    server.createContext("/identity/me", exchange => routes.current(exchange))
    server.createContext("/identity/accounts", exchange => routes.accounts(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/"))
    finally server.stop(0)
  }

  private def get(
    uri: URI,
    authorization: Option[String] = None,
    sessionToken: Option[String] = None
  ): RouteResponse = {
    val builder = HttpRequest.newBuilder(uri).GET()
    authorization.foreach(value => builder.header("Authorization", value))
    sessionToken.foreach(value => builder.header("X-Session-Token", value))
    val response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    RouteResponse(response.statusCode(), response.body())
  }

  private def postJson(uri: URI, body: String): RouteResponse = {
    val request = HttpRequest
      .newBuilder(uri)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    RouteResponse(response.statusCode(), response.body())
  }

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
