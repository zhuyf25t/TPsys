package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Method, Request}

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{
  FriendRequestMailMetadata,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailImportance,
  MailKind,
  MailReadState,
  MailRecord
}
import slaydemo.backend.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestService,
  FriendRequestSubmissionResult
}

object SocialHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    listParsesOwnerAndRejectsVisitor()
    createParsesHandlesAndValidationErrors()
    createRejectsNonObjectBody()
    respondParsesDecisionAndMapsErrors()
    respondRejectsNonObjectBody()

    println("Social http4s contract checks passed")
  }

  private def listParsesOwnerAndRejectsVisitor(): Unit = {
    val service = RecordingFriendRequestService()
    service.requests = Vector(friendRequest())

    val success = run(service, Request[IO](method = Method.GET, uri = uri"/api/social/friend-requests?ownerHandle=Alice"))
    val visitor = run(service, Request[IO](method = Method.GET, uri = uri"/social/friend-requests?ownerHandle=visitor"))
    val missing = run(service, Request[IO](method = Method.GET, uri = uri"/social/friend-requests"))

    assertEquals("list status", success.status, 200)
    assertContains("list wrapper", success.body, """"requests":[""")
    assertContains("list status field", success.body, """"status":"pending"""")
    assertEquals("list owner call", service.listCalls, Vector(PlayerHandle("Alice")))

    assertEquals("visitor list status", visitor.status, 403)
    assertContains("visitor list code", visitor.body, """"code":"visitor_not_allowed"""")
    assertEquals("visitor list does not add call", service.listCalls.length, 1)

    assertEquals("missing list owner status", missing.status, 400)
    assertContains("missing list owner code", missing.body, """"code":"missing_owner"""")
    assertEquals("missing list owner does not add call", service.listCalls.length, 1)
  }

  private def createParsesHandlesAndValidationErrors(): Unit = {
    val service = RecordingFriendRequestService()

    val created = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/api/social/friend-requests")
        .withEntity("""{"sourceHandle":"Alice","targetHandle":"Bob"}""")
    )
    val visitor = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests")
        .withEntity("""{"sourceHandle":"visitor","targetHandle":"Bob"}""")
    )

    assertEquals("create status", created.status, 200)
    assertContains("create created", created.body, """"created":true""")
    assertContains("create mail metadata", created.body, """"friendRequestStatus":"pending"""")
    assertEquals("create call", service.createCalls.head, PlayerHandle("Alice") -> PlayerHandle("Bob"))

    assertEquals("visitor create status", visitor.status, 403)
    assertContains("visitor create code", visitor.body, """"code":"visitor_not_allowed"""")
    assertEquals("visitor create does not call service", service.createCalls.length, 1)

    service.createResults = Vector(Left(FriendRequestCreateError.InvalidHandles))
    val serviceError = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests")
        .withEntity("""{"sourceHandle":"Alice","targetHandle":"Alice"}""")
    )

    assertEquals("service create error status", serviceError.status, 400)
    assertContains("service create error code", serviceError.body, """"code":"invalid_handles"""")
    assertEquals("service create error call count", service.createCalls.length, 2)
  }

  private def createRejectsNonObjectBody(): Unit = {
    val service = RecordingFriendRequestService()
    val response = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests")
        .withEntity("[]")
    )

    assertEquals("create non-object status", response.status, 400)
    assertEquals(
      "create non-object body",
      response.body,
      """{"error":"Request body must be a JSON object with string fields.","code":"bad_request"}"""
    )
    assertEquals("create non-object does not call service", service.createCalls, Vector.empty)
  }

  private def respondParsesDecisionAndMapsErrors(): Unit = {
    val service = RecordingFriendRequestService()

    val accepted = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/api/social/friend-requests/respond")
        .withEntity("""{"requestId":"friend-route","actorHandle":"Bob","decision":"accepted"}""")
    )
    val invalidDecision = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests/respond")
        .withEntity("""{"requestId":"friend-route","actorHandle":"Bob","decision":"maybe"}""")
    )

    assertEquals("respond status", accepted.status, 200)
    assertContains("respond request", accepted.body, """"id":"friend-route"""")
    assertContains("respond mail metadata", accepted.body, """"friendRequestStatus":"accepted"""")
    assertEquals(
      "respond call",
      service.respondCalls.head,
      (FriendRequestId("friend-route"), PlayerHandle("Bob"), FriendRequestDecision.Accepted)
    )

    assertEquals("invalid decision status", invalidDecision.status, 400)
    assertContains("invalid decision code", invalidDecision.body, """"code":"invalid_decision"""")
    assertEquals("invalid decision does not call service", service.respondCalls.length, 1)

    service.respondResults = Vector(Left(FriendRequestRespondError.RequestNotFound), Left(FriendRequestRespondError.Forbidden))
    val notFound = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests/respond")
        .withEntity("""{"requestId":"missing","actorHandle":"Bob","decision":"accepted"}""")
    )
    val forbidden = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests/respond")
        .withEntity("""{"requestId":"friend-route","actorHandle":"Alice","decision":"rejected"}""")
    )

    assertEquals("not found status", notFound.status, 404)
    assertContains("not found code", notFound.body, """"code":"request_not_found"""")
    assertEquals("forbidden status", forbidden.status, 403)
    assertContains("forbidden code", forbidden.body, """"code":"forbidden"""")
    assertEquals("error respond calls", service.respondCalls.length, 3)
  }

  private def respondRejectsNonObjectBody(): Unit = {
    val service = RecordingFriendRequestService()
    val response = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/social/friend-requests/respond")
        .withEntity("[]")
    )

    assertEquals("respond non-object status", response.status, 400)
    assertEquals(
      "respond non-object body",
      response.body,
      """{"error":"Request body must be a JSON object with string fields.","code":"bad_request"}"""
    )
    assertEquals("respond non-object does not call service", service.respondCalls, Vector.empty)
  }

  private def run(service: FriendRequestService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.socialRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingFriendRequestService extends FriendRequestService {
    var requests: Vector[FriendRequestRecord] = Vector.empty
    var createResults: Vector[Either[FriendRequestCreateError, FriendRequestSubmissionResult]] = Vector.empty
    var respondResults: Vector[Either[FriendRequestRespondError, FriendRequestResponseResult]] = Vector.empty
    var listCalls: Vector[PlayerHandle] = Vector.empty
    var createCalls: Vector[(PlayerHandle, PlayerHandle)] = Vector.empty
    var respondCalls: Vector[(FriendRequestId, PlayerHandle, FriendRequestDecision)] = Vector.empty

    override def create(
      sourceHandle: PlayerHandle,
      targetHandle: PlayerHandle
    ): Either[FriendRequestCreateError, FriendRequestSubmissionResult] = {
      createCalls = createCalls :+ (sourceHandle -> targetHandle)
      takeResult(
        createResults,
        remaining => createResults = remaining,
        Right(FriendRequestSubmissionResult.Created(friendRequest(sourceHandle = sourceHandle, targetHandle = targetHandle), mailRecord()))
      )
    }

    override def respond(
      requestId: FriendRequestId,
      actorHandle: PlayerHandle,
      decision: FriendRequestDecision
    ): Either[FriendRequestRespondError, FriendRequestResponseResult] = {
      respondCalls = respondCalls :+ (requestId, actorHandle, decision)
      val status = FriendRequestDecision.statusFor(decision)
      takeResult(
        respondResults,
        remaining => respondResults = remaining,
        Right(
          FriendRequestResponseResult.Updated(
            friendRequest(id = requestId, status = status, respondedAt = Some(EpochMillis(2_000L))),
            mailRecord(status = mailStatusFor(status), sourceHandle = actorHandle)
          )
        )
      )
    }

    override def list(ownerHandle: PlayerHandle): Vector[FriendRequestRecord] = {
      listCalls = listCalls :+ ownerHandle
      requests
    }

    override def find(requestId: FriendRequestId): Option[FriendRequestRecord] =
      requests.find(_.id == requestId)

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

  private def friendRequest(
    id: FriendRequestId = FriendRequestId("friend-route"),
    sourceHandle: PlayerHandle = PlayerHandle("Alice"),
    targetHandle: PlayerHandle = PlayerHandle("Bob"),
    createdAt: EpochMillis = EpochMillis(1_000L),
    status: FriendRequestStatus = FriendRequestStatus.Pending,
    respondedAt: Option[EpochMillis] = None
  ): FriendRequestRecord =
    FriendRequestRecord(
      id = id,
      sourceHandle = sourceHandle,
      targetHandle = targetHandle,
      createdAt = createdAt,
      status = status,
      respondedAt = respondedAt
    )

  private def mailRecord(
    id: MailId = MailId("mail-route"),
    ownerHandle: PlayerHandle = PlayerHandle("Bob"),
    status: MailFriendRequestStatus = MailFriendRequestStatus.Pending,
    sourceHandle: PlayerHandle = PlayerHandle("Alice")
  ): MailRecord =
    MailRecord(
      id = id,
      ownerHandle = ownerHandle,
      kind = MailKind.Friend,
      subject = "Friend request",
      excerpt = "Friend request mail",
      senderLabel = "Friend request",
      readState = MailReadState.Unread,
      importance = MailImportance.Normal,
      createdAt = EpochMillis(1_000L),
      sourceBattleId = None,
      sourcePath = Some("/social"),
      sourceLabel = Some("Friend request"),
      governanceMetadata = None,
      friendRequestMetadata = Some(
        FriendRequestMailMetadata(
          requestId = MailFriendRequestId("friend-route"),
          status = status,
          sourceHandle = sourceHandle
        )
      )
    )

  private def mailStatusFor(status: FriendRequestStatus): MailFriendRequestStatus =
    status match {
      case FriendRequestStatus.Pending  => MailFriendRequestStatus.Pending
      case FriendRequestStatus.Accepted => MailFriendRequestStatus.Accepted
      case FriendRequestStatus.Rejected => MailFriendRequestStatus.Rejected
    }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
