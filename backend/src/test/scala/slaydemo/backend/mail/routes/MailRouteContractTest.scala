package slaydemo.backend.mail.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{
  FriendRequestMailMetadata,
  GovernanceMailActorHandle,
  GovernanceMailMetadata,
  GovernanceMailTargetLabel,
  GovernanceMailTargetPath,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailImportance,
  MailKind,
  MailReadState,
  MailRecord
}
import slaydemo.backend.mail.services.{MailReadError, MailService}

object MailRouteContractTest {
  def main(args: Array[String]): Unit = {
    listParsesOwnerAndRendersMetadata()
    listRejectsMissingAndVisitorOwners()
    readParsesCommandAndMapsResult()
    readValidationErrorsDoNotCallService()

    println("Mail route contract checks passed")
  }

  private def listParsesOwnerAndRendersMetadata(): Unit = {
    val service = RecordingMailService()
    service.mails = Vector(
      mailRecord(
        id = MailId("mail-friend"),
        kind = MailKind.Friend,
        friendRequestMetadata = Some(
          FriendRequestMailMetadata(
            requestId = MailFriendRequestId("friend-1"),
            status = MailFriendRequestStatus.Pending,
            sourceHandle = PlayerHandle("Bob")
          )
        )
      ),
      mailRecord(
        id = MailId("mail-governance"),
        kind = MailKind.Governance,
        governanceMetadata = Some(
          GovernanceMailMetadata(
            actorHandle = GovernanceMailActorHandle("admin"),
            targetPath = GovernanceMailTargetPath("/replay/battle-1"),
            targetLabel = GovernanceMailTargetLabel("Replay battle-1")
          )
        )
      )
    )

    withMailServer(service) { uri =>
      val response = get(uri.resolve("/mails?ownerHandle=Alice"))

      assertEquals("list status", response.status, 200)
      assertContains("list wrapper", response.body, """"mails":[""")
      assertContains("friend metadata id", response.body, """"friendRequestId":"friend-1"""")
      assertContains("friend metadata status", response.body, """"friendRequestStatus":"pending"""")
      assertContains("governance metadata actor", response.body, """"governanceActorHandle":"admin"""")
      assertContains("governance metadata path", response.body, """"governanceTargetPath":"/replay/battle-1"""")
      assertEquals("list owner call", service.listCalls, Vector(PlayerHandle("Alice")))
    }
  }

  private def listRejectsMissingAndVisitorOwners(): Unit = {
    val service = RecordingMailService()

    withMailServer(service) { uri =>
      val missing = get(uri.resolve("/mails"))
      val visitor = get(uri.resolve("/mails?ownerHandle=visitor"))

      assertEquals("missing owner status", missing.status, 400)
      assertContains("missing owner code", missing.body, """"code":"missing_owner"""")
      assertEquals("visitor owner status", visitor.status, 403)
      assertContains("visitor owner code", visitor.body, """"code":"visitor_not_allowed"""")
      assertEquals("invalid list requests do not call service", service.listCalls, Vector.empty)
    }
  }

  private def readParsesCommandAndMapsResult(): Unit = {
    val service = RecordingMailService()

    withMailServer(service) { uri =>
      val success = postJson(
        uri.resolve("/mails/read"),
        """{"ownerHandle":"Alice","mailId":"mail-friend"}"""
      )

      assertEquals("mark read status", success.status, 200)
      assertEquals("mark read body", success.body, """{"ok":true}""")
      assertEquals("mark read call", service.markReadCalls, Vector(PlayerHandle("Alice") -> MailId("mail-friend")))

      service.markReadResults = Vector(Left(MailReadError.MailNotFound))
      val notFound = postJson(
        uri.resolve("/mails/read"),
        """{"ownerHandle":"Alice","mailId":"missing"}"""
      )

      assertEquals("mail not found status", notFound.status, 404)
      assertContains("mail not found code", notFound.body, """"code":"mail_not_found"""")
      assertEquals("mail not found call count", service.markReadCalls.length, 2)
    }
  }

  private def readValidationErrorsDoNotCallService(): Unit = {
    val service = RecordingMailService()

    withMailServer(service) { uri =>
      val badBody = postJson(uri.resolve("/mails/read"), "not-json")
      val missingOwner = postJson(
        uri.resolve("/mails/read"),
        """{"mailId":"mail-friend"}"""
      )
      val missingMailId = postJson(
        uri.resolve("/mails/read"),
        """{"ownerHandle":"Alice"}"""
      )
      val visitor = postJson(
        uri.resolve("/mails/read"),
        """{"ownerHandle":"visitor","mailId":"mail-friend"}"""
      )

      assertEquals("bad body status", badBody.status, 400)
      assertContains("bad body code", badBody.body, """"code":"bad_request"""")
      assertEquals("missing read owner status", missingOwner.status, 400)
      assertContains("missing read owner code", missingOwner.body, """"code":"missing_owner"""")
      assertEquals("missing mail id status", missingMailId.status, 400)
      assertContains("missing mail id code", missingMailId.body, """"code":"missing_mail_id"""")
      assertEquals("visitor read status", visitor.status, 403)
      assertContains("visitor read code", visitor.body, """"code":"visitor_not_allowed"""")
      assertEquals("invalid read requests do not call service", service.markReadCalls, Vector.empty)
    }
  }

  private def withMailServer[A](service: RecordingMailService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = MailRoutes(service)
    server.createContext("/mails", exchange => routes.mails(exchange))
    server.createContext("/mails/read", exchange => routes.read(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/"))
    finally server.stop(0)
  }

  private def get(uri: URI): RouteResponse = {
    val request = HttpRequest.newBuilder(uri).GET().build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
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

  private final class RecordingMailService extends MailService {
    var mails: Vector[MailRecord] = Vector.empty
    var markReadResults: Vector[Either[MailReadError, MailRecord]] = Vector.empty
    var listCalls: Vector[PlayerHandle] = Vector.empty
    var markReadCalls: Vector[(PlayerHandle, MailId)] = Vector.empty

    override def list(ownerHandle: PlayerHandle): Vector[MailRecord] = {
      listCalls = listCalls :+ ownerHandle
      mails
    }

    override def markRead(ownerHandle: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord] = {
      markReadCalls = markReadCalls :+ (ownerHandle -> mailId)
      markReadResults match {
        case head +: tail =>
          markReadResults = tail
          head
        case _ =>
          Right(mailRecord(id = mailId, ownerHandle = ownerHandle, unread = false))
      }
    }
  }

  private def mailRecord(
    id: MailId = MailId("mail-route"),
    ownerHandle: PlayerHandle = PlayerHandle("Alice"),
    kind: MailKind = MailKind.System,
    subject: String = "Route mail",
    unread: Boolean = true,
    friendRequestMetadata: Option[FriendRequestMailMetadata] = None,
    governanceMetadata: Option[GovernanceMailMetadata] = None
  ): MailRecord =
    MailRecord(
      id = id,
      ownerHandle = ownerHandle,
      kind = kind,
      subject = subject,
      excerpt = "Route mail excerpt",
      senderLabel = "Route sender",
      readState = MailReadState.fromUnreadFlag(unread),
      importance = MailImportance.Important,
      createdAt = EpochMillis(1_000L),
      sourceBattleId = None,
      sourcePath = Some("/mail/source"),
      sourceLabel = Some("Mail source"),
      governanceMetadata = governanceMetadata,
      friendRequestMetadata = friendRequestMetadata
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
