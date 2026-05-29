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

import route.battle.BattleHttp4sRoutes
import route.bots.BotProfileHttp4sRoutes
import route.governance.GovernanceHttp4sRoutes
import route.health.{HealthHttp4sRoutes, HealthHttpModule}
import route.identity.IdentityHttp4sRoutes
import route.mail.MailHttp4sRoutes
import route.forum.ForumHttp4sRoutes
import route.replay.{ReplayHttp4sRoutes, ReplayHttpModule}
import route.social.SocialHttp4sRoutes
import services.{BackendRepositories, BackendRepositoryFactories}
import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.battle.objects.*
import services.bots.objects.*
import services.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository}
import services.forum.database.{FileForumRepository, InMemoryForumRepository}
import services.forum.objects.*
import services.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository}
import services.governance.objects.*
import services.identity.database.{FileIdentityAccountRepository, InMemoryIdentityAccountRepository}
import services.identity.objects.IdentityAccountSummary
import services.identity.objects.{DisplayName, IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import services.identity.ports.{PasswordVerification, Pbkdf2PasswordHasher, Sha256PasswordHasher}
import services.mail.database.{FileMailRepository, InMemoryMailRepository}
import services.mail.objects.*
import services.replay.database.{FileReplayRepository, InMemoryReplayRepository, ReplayRepository}
import services.replay.objects.*
import services.social.database.{FileFriendRequestRepository, InMemoryFriendRequestRepository}
import services.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import services.battle.microservices.session.services.{
  BattleCommandOwnership,
  BattleCommandSubmitError,
  BattleSessionLookup,
  BattleSessionSeed,
  BattleRoomLifecycleSink,
  BattleStateReadError,
  BattleStateService,
  InMemoryBattleStateService
}
import services.battle.microservices.queue.services.{
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleQueueStatusError,
  BattleRoomError
}
import services.battle.microservices.projections.services.{
  BattleFinishProjectionFailureReporter,
  DefaultBattleFinishProjector
}
import services.battle.microservices.results.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjector, BattlePlacement, RatingDelta}
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

private[contract] object HealthHttp4sRouteContractTest:
  def run(): Unit =
    getRendersHealthPathAliases()
    apiMessageRouteUsesClassNameDerivedPath()
    apiMessageRouteDoesNotRewriteApiName()
    unsupportedMethodIsRejected()

  private def getRendersHealthPathAliases(): Unit =
    Vector("/health", "/api/health").foreach { path =>
      val service = RecordingHealthService()
      val response = RouteContractSupport.runRoute(
        HealthHttp4sRoutes.routes(service),
        Request[IO](method = Method.GET, uri = org.http4s.Uri.unsafeFromString(path))
      )

      ContractAssertions.assertEquals(s"$path status", response.status, 200)
      ContractAssertions.assertContains(s"$path health ok", response.body, """"status":"ok"""")
      ContractAssertions.assertContains(s"$path service", response.body, """"service":"route-health"""")
      ContractAssertions.assertContains(s"$path port", response.body, """"port":18080""")
      ContractAssertions.assertContains(s"$path storage mode", response.body, """"storageMode":"memory"""")
      ContractAssertions.assertEquals(s"$path service call count", service.currentCalls, 1)
    }

  private def apiMessageRouteUsesClassNameDerivedPath(): Unit =
    val service = RecordingHealthService()
    val response = RouteContractSupport.runRoute(
      HealthHttpModule.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/health").withEntity("{}")
    )

    ContractAssertions.assertEquals("health api message status", response.status, 200)
    ContractAssertions.assertContains("health api message ok", response.body, """"status":"ok"""")
    ContractAssertions.assertContains("health api message service", response.body, """"service":"route-health"""")
    ContractAssertions.assertEquals("health api message service call count", service.currentCalls, 1)

  private def apiMessageRouteDoesNotRewriteApiName(): Unit =
    val service = RecordingHealthService()
    val response = RouteContractSupport.runRoute(
      HealthHttpModule.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/HEALTH").withEntity("{}")
    )

    ContractAssertions.assertEquals("health api message uppercase path status", response.status, 404)
    ContractAssertions.assertEquals("health api message uppercase path does not call service", service.currentCalls, 0)

  private def unsupportedMethodIsRejected(): Unit =
    val service = RecordingHealthService()
    val response = RouteContractSupport.runRoute(
      HealthHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/health").withEntity("{}")
    )

    ContractAssertions.assertEquals("health unsupported method status", response.status, 405)
    ContractAssertions.assertContains("health unsupported method code", response.body, """"code":"method_not_allowed"""")
    ContractAssertions.assertEquals("health unsupported method does not call service", service.currentCalls, 0)

  private final class RecordingHealthService extends HealthService:
    var currentCalls: Int = 0

    override def current: HealthResponse =
      currentCalls += 1
      HealthResponse(
        status = HealthStatus.Ok,
        service = ServiceName("route-health"),
        port = ServicePort.unsafe(18080),
        storageMode = StorageMode.Memory
      )

private[contract] object IdentityHttp4sRouteContractTest:
  def run(): Unit =
    registerParsesCommandAndRendersAuth()
    registerRejectsInvalidRequest()
    accountsRendersActiveSummaries()
    currentSessionParsesAuthorizationHeader()

  private def registerParsesCommandAndRendersAuth(): Unit =
    val service = RecordingIdentityService()
    val response = RouteContractSupport.runRoute(
      IdentityHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/identity/register")
        .withEntity("""{"handle":"Alice","password":"safe-pass","skinId":"soldier"}""")
    )

    ContractAssertions.assertEquals("register status", response.status, 200)
    ContractAssertions.assertContains("register handle", response.body, """"handle":"Alice"""")
    ContractAssertions.assertContains("register skin", response.body, """"skinId":"soldier"""")
    ContractAssertions.assertContains("register session", response.body, """"session":"session-alice"""")
    ContractAssertions.assertEquals("register command count", service.registerCommands.length, 1)
    ContractAssertions.assertEquals("register command handle", service.registerCommands.head.handle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("register command skin", service.registerCommands.head.skinId, SkinId.Soldier)

  private def registerRejectsInvalidRequest(): Unit =
    val service = RecordingIdentityService()
    val response = RouteContractSupport.runRoute(
      IdentityHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/identity/register")
        .withEntity("""{"handle":"ab","password":"safe-pass","skinId":"soldier"}""")
    )

    ContractAssertions.assertEquals("invalid register status", response.status, 400)
    ContractAssertions.assertContains("invalid register code", response.body, """"code":"invalid_handle"""")
    ContractAssertions.assertEquals("invalid register does not call service", service.registerCommands, Vector.empty)

  private def accountsRendersActiveSummaries(): Unit =
    val service = RecordingIdentityService()
    service.accountSummaries = Vector(
      IdentityAccountSummary(PlayerHandle("admin"), DisplayName("admin"), SkinId.Blue),
      IdentityAccountSummary(PlayerHandle("Alice"), DisplayName("Alice"), SkinId.Survivor)
    )
    val response = RouteContractSupport.runRoute(
      IdentityHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/identity/accounts")
    )

    ContractAssertions.assertEquals("accounts status", response.status, 200)
    ContractAssertions.assertContains("accounts wrapper", response.body, """"accounts":[""")
    ContractAssertions.assertContains("accounts admin", response.body, """"handle":"admin"""")
    ContractAssertions.assertContains("accounts skin", response.body, """"skinId":"survivor"""")
    ContractAssertions.assertEquals("accounts list called", service.listActiveAccountsCalls, 1)

  private def currentSessionParsesAuthorizationHeader(): Unit =
    val service = RecordingIdentityService()
    val response = RouteContractSupport.runRoute(
      IdentityHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/identity/me", headers = header("Authorization", "Bearer session-alice"))
    )

    ContractAssertions.assertEquals("current status", response.status, 200)
    ContractAssertions.assertContains("current session", response.body, """"session":"session-alice"""")
    ContractAssertions.assertEquals("current token", service.currentCalls, Vector(Some(SessionToken("session-alice"))))

  private def header(name: String, value: String): Headers =
    Headers(Header.Raw(CIString(name), value))

  private final class RecordingIdentityService extends IdentityService:
    var registerResults: Vector[Either[IdentityRegistrationError, IdentityAccount]] = Vector.empty
    var sessionResults: Vector[Either[IdentitySessionError, IdentityAccount]] = Vector.empty
    var currentResults: Vector[Either[IdentityCurrentSessionError, IdentityAccount]] = Vector.empty
    var accountSummaries: Vector[IdentityAccountSummary] = Vector.empty
    var registerCommands: Vector[IdentityRegistrationCommand] = Vector.empty
    var sessionCommands: Vector[IdentitySessionCommand] = Vector.empty
    var currentCalls: Vector[Option[SessionToken]] = Vector.empty
    var listActiveAccountsCalls: Int = 0

    override def register(command: IdentityRegistrationCommand): IO[Either[IdentityRegistrationError, IdentityAccount]] =
      registerCommands = registerCommands :+ command
      IO.pure(takeResult(
        registerResults,
        remaining => registerResults = remaining,
        Right(account(command.handle, command.skinId, Some(SessionToken(s"session-${command.handle.key}"))))
      ))

    override def issueSession(command: IdentitySessionCommand): IO[Either[IdentitySessionError, IdentityAccount]] =
      sessionCommands = sessionCommands :+ command
      IO.pure(takeResult(
        sessionResults,
        remaining => sessionResults = remaining,
        Right(account(command.handle, SkinId.Blue, Some(SessionToken(s"session-${command.handle.key}"))))
      ))

    override def current(sessionToken: Option[SessionToken]): IO[Either[IdentityCurrentSessionError, IdentityAccount]] =
      currentCalls = currentCalls :+ sessionToken
      IO.pure(takeResult(
        currentResults,
        remaining => currentResults = remaining,
        sessionToken match
          case Some(token) =>
            Right(account(PlayerHandle(token.value.stripPrefix("session-").capitalize), SkinId.Blue, Some(token)))
          case None =>
            Left(IdentityCurrentSessionError.MissingSession)
      ))

    override def listActiveAccounts(): IO[Vector[IdentityAccountSummary]] =
      listActiveAccountsCalls += 1
      IO.pure(accountSummaries)

    private def takeResult[E, A](
      results: Vector[Either[E, A]],
      saveRemaining: Vector[Either[E, A]] => Unit,
      default: Either[E, A]
    ): Either[E, A] =
      results match
        case head +: tail =>
          saveRemaining(tail)
          head
        case _ =>
          default

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

private[contract] object MailHttp4sRouteContractTest:
  def run(): Unit =
    listParsesOwnerAndRendersMails()
    listRejectsMissingOwner()
    markReadParsesBodyAndRendersOk()
    markReadMapsMissingMail()

  private def listParsesOwnerAndRendersMails(): Unit =
    val service = RecordingMailService()
    service.listRecords = Vector(mailRecord(MailId("mail-1"), PlayerHandle("Alice"), MailReadState.Unread))
    val response = RouteContractSupport.runRoute(
      MailHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/mails?ownerHandle=Alice")
    )

    ContractAssertions.assertEquals("mail list status", response.status, 200)
    ContractAssertions.assertContains("mail list wrapper", response.body, """"mails":[""")
    ContractAssertions.assertContains("mail list id", response.body, """"id":"mail-1"""")
    ContractAssertions.assertContains("mail list owner", response.body, """"ownerHandle":"Alice"""")
    ContractAssertions.assertContains("mail list kind", response.body, """"kind":"system"""")
    ContractAssertions.assertContains("mail list unread", response.body, """"unread":true""")
    ContractAssertions.assertContains("mail list important", response.body, """"important":true""")
    ContractAssertions.assertEquals("mail list owner call", service.listOwnerHandles, Vector(PlayerHandle("Alice")))

  private def listRejectsMissingOwner(): Unit =
    val service = RecordingMailService()
    val response = RouteContractSupport.runRoute(
      MailHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/mails")
    )

    ContractAssertions.assertEquals("mail list missing owner status", response.status, 400)
    ContractAssertions.assertContains("mail list missing owner code", response.body, """"code":"missing_owner"""")
    ContractAssertions.assertEquals("mail list missing owner no call", service.listOwnerHandles, Vector.empty)

  private def markReadParsesBodyAndRendersOk(): Unit =
    val service = RecordingMailService()
    val response = RouteContractSupport.runRoute(
      MailHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/mails/read")
        .withEntity("""{"ownerHandle":"Alice","mailId":"mail-1"}""")
    )

    ContractAssertions.assertEquals("mail read status", response.status, 200)
    ContractAssertions.assertContains("mail read ok", response.body, """"ok":true""")
    ContractAssertions.assertEquals(
      "mail read command",
      service.markReadCalls,
      Vector((PlayerHandle("Alice"), MailId("mail-1")))
    )

  private def markReadMapsMissingMail(): Unit =
    val service = RecordingMailService()
    service.markReadResult = Left(MailReadError.MailNotFound)
    val response = RouteContractSupport.runRoute(
      MailHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/mails/read")
        .withEntity("""{"ownerHandle":"Alice","mailId":"missing-mail"}""")
    )

    ContractAssertions.assertEquals("mail read missing status", response.status, 404)
    ContractAssertions.assertContains("mail read missing code", response.body, """"code":"mail_not_found"""")
    ContractAssertions.assertEquals(
      "mail read missing command",
      service.markReadCalls,
      Vector((PlayerHandle("Alice"), MailId("missing-mail")))
    )

  private final class RecordingMailService extends MailService:
    var listRecords: Vector[MailRecord] = Vector.empty
    var listOwnerHandles: Vector[PlayerHandle] = Vector.empty
    var markReadCalls: Vector[(PlayerHandle, MailId)] = Vector.empty
    var markReadResult: Either[MailReadError, MailRecord] =
      Right(mailRecord(MailId("mail-1"), PlayerHandle("Alice"), MailReadState.Read))

    override def list(ownerHandle: PlayerHandle): IO[Vector[MailRecord]] =
      listOwnerHandles = listOwnerHandles :+ ownerHandle
      IO.pure(listRecords)

    override def markRead(ownerHandle: PlayerHandle, mailId: MailId): IO[Either[MailReadError, MailRecord]] =
      markReadCalls = markReadCalls :+ ((ownerHandle, mailId))
      IO.pure(markReadResult)

  private def mailRecord(id: MailId, ownerHandle: PlayerHandle, readState: MailReadState): MailRecord =
    MailRecord(
      id = id,
      ownerHandle = ownerHandle,
      kind = MailKind.System,
      subject = "Welcome",
      excerpt = "Backend mailbox ready.",
      senderLabel = "System",
      readState = readState,
      importance = MailImportance.Important,
      createdAt = EpochMillis(1000),
      sourceBattleId = None,
      sourcePath = None,
      sourceLabel = None,
      governanceMetadata = None,
      friendRequestMetadata = None
    )

private[contract] object SocialHttp4sRouteContractTest:
  def run(): Unit =
    listParsesOwnerAndRendersRequests()
    listRejectsMissingOwner()
    createParsesHandlesAndRendersCreated()
    respondParsesCommandAndRendersUpdated()
    respondMapsMissingRequest()

  private def listParsesOwnerAndRendersRequests(): Unit =
    val service = RecordingFriendRequestService()
    service.listRecords = Vector(friendRequest(FriendRequestStatus.Pending))
    val response = RouteContractSupport.runRoute(
      SocialHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/social/friend-requests?ownerHandle=Alice")
    )

    ContractAssertions.assertEquals("social list status", response.status, 200)
    ContractAssertions.assertContains("social list wrapper", response.body, """"requests":[""")
    ContractAssertions.assertContains("social list id", response.body, """"id":"friend-request-1"""")
    ContractAssertions.assertContains("social list source", response.body, """"sourceHandle":"Alice"""")
    ContractAssertions.assertContains("social list target", response.body, """"targetHandle":"Bob"""")
    ContractAssertions.assertContains("social list status body", response.body, """"status":"pending"""")
    ContractAssertions.assertEquals("social list owner call", service.listOwnerHandles, Vector(PlayerHandle("Alice")))

  private def listRejectsMissingOwner(): Unit =
    val service = RecordingFriendRequestService()
    val response = RouteContractSupport.runRoute(
      SocialHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/social/friend-requests")
    )

    ContractAssertions.assertEquals("social list missing owner status", response.status, 400)
    ContractAssertions.assertContains("social list missing owner code", response.body, """"code":"missing_owner"""")
    ContractAssertions.assertEquals("social list missing owner no call", service.listOwnerHandles, Vector.empty)

  private def createParsesHandlesAndRendersCreated(): Unit =
    val service = RecordingFriendRequestService()
    val response = RouteContractSupport.runRoute(
      SocialHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/social/friend-requests")
        .withEntity("""{"sourceHandle":"Alice","targetHandle":"Bob"}""")
    )

    ContractAssertions.assertEquals("social create status", response.status, 200)
    ContractAssertions.assertContains("social create created", response.body, """"created":true""")
    ContractAssertions.assertContains("social create already sent", response.body, """"alreadySent":false""")
    ContractAssertions.assertContains("social create id", response.body, """"id":"friend-request-1"""")
    ContractAssertions.assertEquals(
      "social create handles",
      service.createCalls,
      Vector((PlayerHandle("Alice"), PlayerHandle("Bob")))
    )

  private def respondParsesCommandAndRendersUpdated(): Unit =
    val service = RecordingFriendRequestService()
    val response = RouteContractSupport.runRoute(
      SocialHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/social/friend-requests/respond")
        .withEntity("""{"requestId":"friend-request-1","actorHandle":"Bob","decision":"accepted"}""")
    )

    ContractAssertions.assertEquals("social respond status", response.status, 200)
    ContractAssertions.assertContains("social respond request", response.body, """"request":{""")
    ContractAssertions.assertContains("social respond accepted", response.body, """"status":"accepted"""")
    ContractAssertions.assertContains("social respond mail", response.body, """"mail":{""")
    ContractAssertions.assertEquals(
      "social respond command",
      service.respondCalls,
      Vector((FriendRequestId("friend-request-1"), PlayerHandle("Bob"), FriendRequestDecision.Accepted))
    )

  private def respondMapsMissingRequest(): Unit =
    val service = RecordingFriendRequestService()
    service.respondResult = Left(FriendRequestRespondError.RequestNotFound)
    val response = RouteContractSupport.runRoute(
      SocialHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/social/friend-requests/respond")
        .withEntity("""{"requestId":"missing-request","actorHandle":"Bob","decision":"rejected"}""")
    )

    ContractAssertions.assertEquals("social respond missing request status", response.status, 404)
    ContractAssertions.assertContains("social respond missing request code", response.body, """"code":"request_not_found"""")
    ContractAssertions.assertEquals(
      "social respond missing request command",
      service.respondCalls,
      Vector((FriendRequestId("missing-request"), PlayerHandle("Bob"), FriendRequestDecision.Rejected))
    )

  private final class RecordingFriendRequestService extends FriendRequestService:
    var listRecords: Vector[FriendRequestRecord] = Vector.empty
    var listOwnerHandles: Vector[PlayerHandle] = Vector.empty
    var createCalls: Vector[(PlayerHandle, PlayerHandle)] = Vector.empty
    var respondCalls: Vector[(FriendRequestId, PlayerHandle, FriendRequestDecision)] = Vector.empty
    var createResult: Either[FriendRequestCreateError, FriendRequestSubmissionResult] =
      Right(FriendRequestSubmissionResult.Created(friendRequest(FriendRequestStatus.Pending), socialMail))
    var respondResult: Either[FriendRequestRespondError, FriendRequestResponseResult] =
      Right(FriendRequestResponseResult.Updated(friendRequest(FriendRequestStatus.Accepted), socialMail))

    override def create(
      sourceHandle: PlayerHandle,
      targetHandle: PlayerHandle
    ): IO[Either[FriendRequestCreateError, FriendRequestSubmissionResult]] =
      createCalls = createCalls :+ ((sourceHandle, targetHandle))
      IO.pure(createResult)

    override def respond(
      requestId: FriendRequestId,
      actorHandle: PlayerHandle,
      decision: FriendRequestDecision
    ): IO[Either[FriendRequestRespondError, FriendRequestResponseResult]] =
      respondCalls = respondCalls :+ ((requestId, actorHandle, decision))
      IO.pure(respondResult)

    override def list(ownerHandle: PlayerHandle): IO[Vector[FriendRequestRecord]] =
      listOwnerHandles = listOwnerHandles :+ ownerHandle
      IO.pure(listRecords)

    override def find(requestId: FriendRequestId): IO[Option[FriendRequestRecord]] =
      IO.pure(listRecords.find(_.id == requestId))

  private def friendRequest(status: FriendRequestStatus): FriendRequestRecord =
    FriendRequestRecord(
      id = FriendRequestId("friend-request-1"),
      sourceHandle = PlayerHandle("Alice"),
      targetHandle = PlayerHandle("Bob"),
      createdAt = EpochMillis(1000),
      status = status,
      respondedAt = if status == FriendRequestStatus.Pending then None else Some(EpochMillis(2000))
    )

  private def socialMail: MailRecord =
    MailRecord(
      id = MailId("mail-friend-request-1"),
      ownerHandle = PlayerHandle("Alice"),
      kind = MailKind.Friend,
      subject = "Friend request",
      excerpt = "Friend request updated.",
      senderLabel = "Social",
      readState = MailReadState.Unread,
      importance = MailImportance.Normal,
      createdAt = EpochMillis(1000),
      sourceBattleId = None,
      sourcePath = None,
      sourceLabel = None,
      governanceMetadata = None,
      friendRequestMetadata = None
    )

private[contract] object ForumHttp4sRouteContractTest:
  def run(): Unit =
    listParsesViewerAndRendersTopics()
    loadParsesTopicIdAndRendersTopic()
    createParsesBodyAndRendersCreated()
    createRejectsInvalidTitleBeforeService()
    addReplyParsesTopicAndBody()
    topicVoteParsesVoteCommand()
    replyVoteParsesReplyAndVoteCommand()

  private def listParsesViewerAndRendersTopics(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/forum/topics?viewer=Alice")
    )

    ContractAssertions.assertEquals("forum list status", response.status, 200)
    ContractAssertions.assertContains("forum list wrapper", response.body, """"topics":[""")
    ContractAssertions.assertContains("forum list topic id", response.body, """"id":"topic-1"""")
    ContractAssertions.assertContains("forum list title", response.body, """"title":"Balance notes"""")
    ContractAssertions.assertContains("forum list viewer vote", response.body, """"viewerVote":"up"""")
    ContractAssertions.assertEquals("forum list viewer call", service.listCalls, Vector(Some(PlayerHandle("Alice"))))

  private def loadParsesTopicIdAndRendersTopic(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.GET, uri = uri"/api/forum/topics/topic-1?author=Alice")
    )

    ContractAssertions.assertEquals("forum load status", response.status, 200)
    ContractAssertions.assertContains("forum load wrapper", response.body, """"topic":{""")
    ContractAssertions.assertContains("forum load topic", response.body, """"id":"topic-1"""")
    ContractAssertions.assertEquals(
      "forum load call",
      service.loadCalls,
      Vector((ForumTopicId("topic-1"), Some(PlayerHandle("Alice"))))
    )

  private def createParsesBodyAndRendersCreated(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics")
        .withEntity("""{"title":"Balance notes","body":"Weapon tuning proposal","tag":"balance","author":"Alice"}""")
    )

    ContractAssertions.assertEquals("forum create status", response.status, 201)
    ContractAssertions.assertContains("forum create wrapper", response.body, """"topic":{""")
    ContractAssertions.assertEquals("forum create command count", service.createCommands.length, 1)
    ContractAssertions.assertEquals("forum create title", service.createCommands.head.title, ForumTitle("Balance notes"))
    ContractAssertions.assertEquals("forum create body", service.createCommands.head.body, ForumBody("Weapon tuning proposal"))
    ContractAssertions.assertEquals("forum create tag", service.createCommands.head.tag, ForumTag("balance"))
    ContractAssertions.assertEquals("forum create author", service.createCommands.head.authorHandle, PlayerHandle("Alice"))

  private def createRejectsInvalidTitleBeforeService(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics")
        .withEntity("""{"title":"","body":"Weapon tuning proposal","tag":"balance","author":"Alice"}""")
    )

    ContractAssertions.assertEquals("forum invalid create status", response.status, 400)
    ContractAssertions.assertContains("forum invalid create code", response.body, """"code":"invalid_title"""")
    ContractAssertions.assertEquals("forum invalid create no service call", service.createCommands, Vector.empty)

  private def addReplyParsesTopicAndBody(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics/topic-1/replies")
        .withEntity("""{"body":"I agree with this change.","author":"Bob"}""")
    )

    ContractAssertions.assertEquals("forum add reply status", response.status, 200)
    ContractAssertions.assertContains("forum add reply wrapper", response.body, """"topic":{""")
    ContractAssertions.assertEquals("forum add reply command count", service.addReplyCommands.length, 1)
    ContractAssertions.assertEquals("forum add reply topic", service.addReplyCommands.head.topicId, ForumTopicId("topic-1"))
    ContractAssertions.assertEquals("forum add reply body", service.addReplyCommands.head.body, ForumBody("I agree with this change."))
    ContractAssertions.assertEquals("forum add reply author", service.addReplyCommands.head.authorHandle, PlayerHandle("Bob"))

  private def topicVoteParsesVoteCommand(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics/topic-1/votes")
        .withEntity("""{"author":"Alice","vote":"down"}""")
    )

    ContractAssertions.assertEquals("forum topic vote status", response.status, 200)
    ContractAssertions.assertEquals("forum topic vote command count", service.topicVoteCommands.length, 1)
    ContractAssertions.assertEquals("forum topic vote topic", service.topicVoteCommands.head.topicId, ForumTopicId("topic-1"))
    ContractAssertions.assertEquals("forum topic vote author", service.topicVoteCommands.head.authorHandle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("forum topic vote choice", service.topicVoteCommands.head.vote, Some(ForumVoteChoice.Down))

  private def replyVoteParsesReplyAndVoteCommand(): Unit =
    val service = RecordingForumService()
    val response = RouteContractSupport.runRoute(
      ForumHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics/topic-1/replies/reply-1/votes")
        .withEntity("""{"author":"Alice","vote":"up"}""")
    )

    ContractAssertions.assertEquals("forum reply vote status", response.status, 200)
    ContractAssertions.assertEquals("forum reply vote command count", service.replyVoteCommands.length, 1)
    ContractAssertions.assertEquals("forum reply vote topic", service.replyVoteCommands.head.topicId, ForumTopicId("topic-1"))
    ContractAssertions.assertEquals("forum reply vote reply", service.replyVoteCommands.head.replyId, ForumReplyId("reply-1"))
    ContractAssertions.assertEquals("forum reply vote author", service.replyVoteCommands.head.authorHandle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("forum reply vote choice", service.replyVoteCommands.head.vote, Some(ForumVoteChoice.Up))

  private final class RecordingForumService extends ForumService:
    var listCalls: Vector[Option[PlayerHandle]] = Vector.empty
    var loadCalls: Vector[(ForumTopicId, Option[PlayerHandle])] = Vector.empty
    var createCommands: Vector[CreateForumTopicCommand] = Vector.empty
    var addReplyCommands: Vector[AddForumReplyCommand] = Vector.empty
    var topicVoteCommands: Vector[SetForumTopicVoteCommand] = Vector.empty
    var replyVoteCommands: Vector[SetForumReplyVoteCommand] = Vector.empty
    var topics: Vector[ForumTopicView] = Vector(forumTopicView())
    var loadedTopic: Option[ForumTopicView] = Some(forumTopicView())
    var createResult: Either[ForumCreateTopicError, ForumTopicView] = Right(forumTopicView())
    var mutationResult: Either[ForumTopicMutationError, ForumTopicView] = Right(forumTopicView())

    override def listTopics(viewerHandle: Option[PlayerHandle]): IO[Vector[ForumTopicView]] =
      listCalls = listCalls :+ viewerHandle
      IO.pure(topics)

    override def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): IO[Option[ForumTopicView]] =
      loadCalls = loadCalls :+ ((topicId, viewerHandle))
      IO.pure(loadedTopic)

    override def createTopic(command: CreateForumTopicCommand): IO[Either[ForumCreateTopicError, ForumTopicView]] =
      createCommands = createCommands :+ command
      IO.pure(createResult)

    override def addReply(command: AddForumReplyCommand): IO[Either[ForumTopicMutationError, ForumTopicView]] =
      addReplyCommands = addReplyCommands :+ command
      IO.pure(mutationResult)

    override def setTopicVote(command: SetForumTopicVoteCommand): IO[Either[ForumTopicMutationError, ForumTopicView]] =
      topicVoteCommands = topicVoteCommands :+ command
      IO.pure(mutationResult)

    override def setReplyVote(command: SetForumReplyVoteCommand): IO[Either[ForumTopicMutationError, ForumTopicView]] =
      replyVoteCommands = replyVoteCommands :+ command
      IO.pure(mutationResult)

  private def forumTopicView(): ForumTopicView =
    ForumTopicView(
      id = ForumTopicId("topic-1"),
      title = ForumTitle("Balance notes"),
      author = PlayerHandle("Alice"),
      excerpt = "Weapon tuning proposal",
      tag = ForumTag("balance"),
      replies = ForumReplyCount(1),
      updatedAt = EpochMillis(2000),
      createdAt = EpochMillis(1000),
      body = ForumBody("Weapon tuning proposal"),
      replyItems = Vector(
        ForumReplyView(
          id = ForumReplyId("reply-1"),
          author = PlayerHandle("Bob"),
          body = ForumBody("I agree with this change."),
          publishedAt = EpochMillis(1500),
          viewerVote = Some(ForumVoteChoice.Down),
          score = ForumScore(-1)
        )
      ),
      viewerVote = Some(ForumVoteChoice.Up),
      score = ForumScore(1)
    )

private[contract] object GovernanceHttp4sRouteContractTest:
  def run(): Unit =
    listContributionAdjustmentsParsesLimitAndRendersRecords()
    createContributionAdjustmentParsesBodyAndRendersMail()
    createContributionAdjustmentRejectsInvalidActorBeforeService()
    listNotificationsParsesFiltersAndRendersRecords()
    listNotificationsReturnsEmptyForInvalidFilterWithoutService()
    createNotificationParsesBodyAndRendersMail()
    createNotificationRejectsInvalidKindBeforeService()

  private def listContributionAdjustmentsParsesLimitAndRendersRecords(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](method = Method.GET, uri = uri"/api/governance/contribution-adjustments?limit=2")
    )

    ContractAssertions.assertEquals("governance adjustment list status", response.status, 200)
    ContractAssertions.assertContains("governance adjustment list wrapper", response.body, """"adjustments":[""")
    ContractAssertions.assertContains("governance adjustment list id", response.body, """"id":"adjustment-1"""")
    ContractAssertions.assertContains("governance adjustment list target", response.body, """"targetHandle":"Alice"""")
    ContractAssertions.assertEquals("governance adjustment list limit", service.adjustmentListLimits, Vector(2))

  private def createContributionAdjustmentParsesBodyAndRendersMail(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](method = Method.POST, uri = uri"/api/governance/contribution-adjustments")
        .withEntity(
          """{"actorHandle":"admin","targetHandle":"Alice","delta":5,"reason":"Great replay","sourceLabel":"Replay review","sourcePath":"/replays/replay-1"}"""
        )
    )

    ContractAssertions.assertEquals("governance adjustment create status", response.status, 200)
    ContractAssertions.assertContains("governance adjustment create ok", response.body, """"ok":true""")
    ContractAssertions.assertContains("governance adjustment create wrapper", response.body, """"adjustment":{""")
    ContractAssertions.assertContains("governance adjustment create mail", response.body, """"mail":{""")
    ContractAssertions.assertEquals("governance adjustment command count", service.adjustmentCommands.length, 1)
    ContractAssertions.assertEquals("governance adjustment actor", service.adjustmentCommands.head.actorHandle, AdminHandle("admin"))
    ContractAssertions.assertEquals("governance adjustment target", service.adjustmentCommands.head.targetHandle, GovernanceTargetHandle("Alice"))
    ContractAssertions.assertEquals("governance adjustment delta", service.adjustmentCommands.head.delta, ContributionDelta(5))
    ContractAssertions.assertEquals("governance adjustment reason", service.adjustmentCommands.head.reason, GovernanceReason("Great replay"))

  private def createContributionAdjustmentRejectsInvalidActorBeforeService(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](method = Method.POST, uri = uri"/api/governance/contribution-adjustments")
        .withEntity(
          """{"actorHandle":"moderator","targetHandle":"Alice","delta":5,"reason":"Great replay","sourceLabel":"Replay review","sourcePath":"/replays/replay-1"}"""
        )
    )

    ContractAssertions.assertEquals("governance adjustment invalid actor status", response.status, 403)
    ContractAssertions.assertContains("governance adjustment invalid actor code", response.body, """"code":"invalid_actor"""")
    ContractAssertions.assertEquals("governance adjustment invalid actor no service call", service.adjustmentCommands, Vector.empty)

  private def listNotificationsParsesFiltersAndRendersRecords(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](
        method = Method.GET,
        uri = org.http4s.Uri.unsafeFromString("/api/governance/admin-notifications?kind=replay_report&targetType=replay&limit=3")
      )
    )

    ContractAssertions.assertEquals("governance notification list status", response.status, 200)
    ContractAssertions.assertContains("governance notification list wrapper", response.body, """"notifications":[""")
    ContractAssertions.assertContains("governance notification list id", response.body, """"id":"notification-1"""")
    ContractAssertions.assertContains("governance notification list kind", response.body, """"kind":"replay_report"""")
    ContractAssertions.assertEquals(
      "governance notification list query",
      service.notificationListCalls,
      Vector((Some(GovernanceReviewKind.ReplayReport), Some(GovernanceReviewTargetType.Replay), 3))
    )

  private def listNotificationsReturnsEmptyForInvalidFilterWithoutService(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](method = Method.GET, uri = uri"/api/governance/admin-notifications?kind=unknown")
    )

    ContractAssertions.assertEquals("governance notification invalid filter status", response.status, 200)
    ContractAssertions.assertContains("governance notification invalid filter empty", response.body, """"notifications":[]""")
    ContractAssertions.assertEquals("governance notification invalid filter no service call", service.notificationListCalls, Vector.empty)

  private def createNotificationParsesBodyAndRendersMail(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](method = Method.POST, uri = uri"/api/governance/admin-notifications")
        .withEntity(
          """{"actorHandle":"Moderator","kind":"replay_report","targetType":"replay","targetId":"replay-1","targetTitle":"Suspicious replay","targetPath":"/replays/replay-1","body":"Please review this replay."}"""
        )
    )

    ContractAssertions.assertEquals("governance notification create status", response.status, 200)
    ContractAssertions.assertContains("governance notification create ok", response.body, """"ok":true""")
    ContractAssertions.assertContains("governance notification create wrapper", response.body, """"notification":{""")
    ContractAssertions.assertContains("governance notification create mail", response.body, """"mail":{""")
    ContractAssertions.assertEquals("governance notification command count", service.notificationCommands.length, 1)
    ContractAssertions.assertEquals("governance notification actor", service.notificationCommands.head.actorHandle, GovernanceActorHandle("Moderator"))
    ContractAssertions.assertEquals("governance notification kind", service.notificationCommands.head.kind, GovernanceReviewKind.ReplayReport)
    ContractAssertions.assertEquals("governance notification target type", service.notificationCommands.head.targetType, GovernanceReviewTargetType.Replay)
    ContractAssertions.assertEquals("governance notification target id", service.notificationCommands.head.targetId, GovernanceReviewTargetId("replay-1"))

  private def createNotificationRejectsInvalidKindBeforeService(): Unit =
    val service = RecordingGovernanceService()
    val response = RouteContractSupport.runRoute(
      GovernanceHttp4sRoutes.routes(service, service),
      Request[IO](method = Method.POST, uri = uri"/api/governance/admin-notifications")
        .withEntity(
          """{"actorHandle":"Moderator","kind":"unknown","targetType":"replay","targetId":"replay-1","targetTitle":"Suspicious replay","targetPath":"/replays/replay-1","body":"Please review this replay."}"""
        )
    )

    ContractAssertions.assertEquals("governance notification invalid kind status", response.status, 400)
    ContractAssertions.assertContains("governance notification invalid kind code", response.body, """"code":"invalid_kind"""")
    ContractAssertions.assertEquals("governance notification invalid kind no service call", service.notificationCommands, Vector.empty)

  private final class RecordingGovernanceService extends ContributionAdjustmentService with GovernanceNotificationService:
    var adjustmentListLimits: Vector[Int] = Vector.empty
    var adjustmentCommands: Vector[ContributionAdjustmentCommand] = Vector.empty
    var notificationListCalls: Vector[(Option[GovernanceReviewKind], Option[GovernanceReviewTargetType], Int)] = Vector.empty
    var notificationCommands: Vector[GovernanceReviewNotificationCommand] = Vector.empty
    var adjustments: Vector[ContributionAdjustmentRecord] = Vector(contributionAdjustmentRecord())
    var notifications: Vector[GovernanceReviewNotificationRecord] = Vector(reviewNotificationRecord())

    override def list(limit: Int): IO[Vector[ContributionAdjustmentRecord]] =
      adjustmentListLimits = adjustmentListLimits :+ limit
      IO.pure(adjustments)

    override def create(command: ContributionAdjustmentCommand): IO[ContributionAdjustmentSubmissionResult] =
      adjustmentCommands = adjustmentCommands :+ command
      IO.pure(ContributionAdjustmentSubmissionResult(contributionAdjustmentRecord(), governanceMailSnapshot()))

    override def listReviewNotifications(
      kind: Option[GovernanceReviewKind],
      targetType: Option[GovernanceReviewTargetType],
      limit: Int
    ): IO[Vector[GovernanceReviewNotificationRecord]] =
      notificationListCalls = notificationListCalls :+ ((kind, targetType, limit))
      IO.pure(notifications)

    override def createReviewNotification(
      command: GovernanceReviewNotificationCommand
    ): IO[GovernanceReviewNotificationSubmissionResult] =
      notificationCommands = notificationCommands :+ command
      IO.pure(GovernanceReviewNotificationSubmissionResult(reviewNotificationRecord(), governanceMailSnapshot()))

  private def contributionAdjustmentRecord(): ContributionAdjustmentRecord =
    ContributionAdjustmentRecord(
      id = ContributionAdjustmentId("adjustment-1"),
      actorHandle = AdminHandle("admin"),
      targetHandle = GovernanceTargetHandle("Alice"),
      delta = ContributionDelta(5),
      reason = GovernanceReason("Great replay"),
      createdAt = EpochMillis(2000),
      sourceLabel = GovernanceSourceLabel("Replay review"),
      sourcePath = GovernanceSourcePath("/replays/replay-1")
    )

  private def reviewNotificationRecord(): GovernanceReviewNotificationRecord =
    GovernanceReviewNotificationRecord(
      id = GovernanceReviewNotificationId("notification-1"),
      actorHandle = GovernanceActorHandle("Moderator"),
      kind = GovernanceReviewKind.ReplayReport,
      targetType = GovernanceReviewTargetType.Replay,
      targetId = GovernanceReviewTargetId("replay-1"),
      targetTitle = GovernanceReviewTargetTitle("Suspicious replay"),
      targetPath = GovernanceReviewTargetPath("/replays/replay-1"),
      body = GovernanceReviewBody("Please review this replay."),
      createdAt = EpochMillis(3000),
      mailId = GovernanceMailSnapshotId("mail-1")
    )

  private def governanceMailSnapshot(): GovernanceMailSnapshot =
    GovernanceMailSnapshot(
      id = GovernanceMailSnapshotId("mail-1"),
      ownerHandle = GovernanceTargetHandle("Alice"),
      kind = MailKind.Governance,
      subject = "Governance notice",
      excerpt = "Please review this replay.",
      senderLabel = "System",
      readState = MailReadState.Unread,
      importance = MailImportance.Important,
      createdAt = EpochMillis(4000),
      governanceMetadata = Some(
        GovernanceMailMetadata(
          actorHandle = GovernanceMailActorHandle("Moderator"),
          targetPath = GovernanceMailTargetPath("/replays/replay-1"),
          targetLabel = GovernanceMailTargetLabel("Suspicious replay")
        )
      )
    )

private[contract] object ReplayHttp4sRouteContractTest:
  def run(): Unit =
    apiMessageRouteUsesClassNameDerivedPath()
    catalogParsesLimitAndRendersReplays()
    detailParsesReplayIdAndRendersReplay()
    recordParsesBodyAndRendersCreated()
    recordRejectsInvalidHandleBeforeService()
    commentsLoadReplayAndRenderComments()
    addCommentParsesBodyAndRendersCreated()

  private def apiMessageRouteUsesClassNameDerivedPath(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttpModule.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/replaycatalog")
        .withEntity("""{"limit":2,"handle":"Alice"}""")
    )

    ContractAssertions.assertEquals("replay api message status", response.status, 200)
    ContractAssertions.assertContains("replay api message wrapper", response.body, """"replays":[""")
    ContractAssertions.assertContains("replay api message id", response.body, """"replayId":"replay-1"""")
    ContractAssertions.assertEquals("replay api message limit", service.listLimits, Vector(2))

  private def catalogParsesLimitAndRendersReplays(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttp4sRoutes.catalogRoutes(service),
      Request[IO](method = Method.GET, uri = uri"/api/replay/catalog?limit=2&handle=Alice")
    )

    ContractAssertions.assertEquals("replay catalog status", response.status, 200)
    ContractAssertions.assertContains("replay catalog wrapper", response.body, """"replays":[""")
    ContractAssertions.assertContains("replay catalog id", response.body, """"replayId":"replay-1"""")
    ContractAssertions.assertContains("replay catalog battle", response.body, """"battleId":"battle-1"""")
    ContractAssertions.assertContains("replay catalog playback", response.body, """"playbackAvailable":true""")
    ContractAssertions.assertEquals("replay catalog limit", service.listLimits, Vector(2))

  private def detailParsesReplayIdAndRendersReplay(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttp4sRoutes.catalogRoutes(service),
      Request[IO](method = Method.GET, uri = uri"/api/replay/catalog/replay-1?handle=Alice")
    )

    ContractAssertions.assertEquals("replay detail status", response.status, 200)
    ContractAssertions.assertContains("replay detail wrapper", response.body, """"replay":{""")
    ContractAssertions.assertContains("replay detail id", response.body, """"replayId":"replay-1"""")
    ContractAssertions.assertContains("replay detail frames", response.body, """"frames":[""")
    ContractAssertions.assertEquals("replay detail load", service.loadCalls, Vector(ReplayId("replay-1")))

  private def recordParsesBodyAndRendersCreated(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttp4sRoutes.catalogRoutes(service),
      Request[IO](method = Method.POST, uri = uri"/api/replay/catalog")
        .withEntity(
          """{"replayId":"replay-2","battleId":"battle-2","handle":"Alice","displayName":"Alice","finishedAt":3000,"finishedAtLabel":"just now","title":"Replay title","modeLabel":"Arena Mode","resultLabel":"Victory","mapLabel":"Island","highlightLine":"Alice won","coverLabel":"Top 1","playersLine":"Alice vs Bob","timelineHint":"30s","score":88,"placement":1,"durationMs":30000,"aliveAtEnd":true,"thumbnailDataUrl":null,"currentLoadout":"rifle","frameCount":1,"playbackAvailable":true,"framesJson":"[{\"tick\":1}]"}"""
        )
    )

    ContractAssertions.assertEquals("replay record status", response.status, 201)
    ContractAssertions.assertContains("replay record wrapper", response.body, """"replay":{""")
    ContractAssertions.assertEquals("replay record command count", service.recordCommands.length, 1)
    ContractAssertions.assertEquals("replay record replay id", service.recordCommands.head.replayId, ReplayId("replay-2"))
    ContractAssertions.assertEquals("replay record battle id", service.recordCommands.head.battleId, BattleId("battle-2"))
    ContractAssertions.assertEquals("replay record handle", service.recordCommands.head.handle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("replay record score", service.recordCommands.head.score, Score(88))
    ContractAssertions.assertEquals("replay record frames", service.recordCommands.head.framesJson, """[{"tick":1}]""")

  private def recordRejectsInvalidHandleBeforeService(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttp4sRoutes.catalogRoutes(service),
      Request[IO](method = Method.POST, uri = uri"/api/replay/catalog")
        .withEntity("""{"replayId":"replay-2","battleId":"battle-2","handle":"","framesJson":"[]"}""")
    )

    ContractAssertions.assertEquals("replay invalid record status", response.status, 400)
    ContractAssertions.assertContains("replay invalid record code", response.body, """"code":"invalid_handle"""")
    ContractAssertions.assertEquals("replay invalid record no service call", service.recordCommands, Vector.empty)

  private def commentsLoadReplayAndRenderComments(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttp4sRoutes.catalogRoutes(service),
      Request[IO](method = Method.GET, uri = uri"/api/replay/catalog/replay-1/comments?limit=3")
    )

    ContractAssertions.assertEquals("replay comments status", response.status, 200)
    ContractAssertions.assertContains("replay comments wrapper", response.body, """"comments":[""")
    ContractAssertions.assertContains("replay comments id", response.body, """"id":"comment-1"""")
    ContractAssertions.assertEquals("replay comments load", service.loadCalls, Vector(ReplayId("replay-1")))
    ContractAssertions.assertEquals("replay comments list", service.listCommentCalls, Vector((ReplayId("replay-1"), 3)))

  private def addCommentParsesBodyAndRendersCreated(): Unit =
    val service = RecordingReplayService()
    val response = RouteContractSupport.runRoute(
      ReplayHttp4sRoutes.catalogRoutes(service),
      Request[IO](method = Method.POST, uri = uri"/api/replay/catalog/replay-1/comments")
        .withEntity("""{"authorHandle":"Bob","body":"Good fight."}""")
    )

    ContractAssertions.assertEquals("replay add comment status", response.status, 201)
    ContractAssertions.assertContains("replay add comment wrapper", response.body, """"comment":{""")
    ContractAssertions.assertEquals("replay add comment load", service.loadCalls, Vector(ReplayId("replay-1")))
    ContractAssertions.assertEquals("replay add comment command count", service.commentCommands.length, 1)
    ContractAssertions.assertEquals("replay add comment replay id", service.commentCommands.head.replayId, ReplayId("replay-1"))
    ContractAssertions.assertEquals("replay add comment author", service.commentCommands.head.authorHandle, PlayerHandle("Bob"))
    ContractAssertions.assertEquals("replay add comment body", service.commentCommands.head.body, "Good fight.")

  private final class RecordingReplayService extends ReplayService:
    var recordCommands: Vector[ReplayRecordCommand] = Vector.empty
    var listLimits: Vector[Int] = Vector.empty
    var loadCalls: Vector[ReplayId] = Vector.empty
    var commentCommands: Vector[ReplayCommentCommand] = Vector.empty
    var listCommentCalls: Vector[(ReplayId, Int)] = Vector.empty
    var recordResult: Either[ReplayRecordError, ReplayRecord] = Right(replayRecord(ReplayId("replay-2"), BattleId("battle-2")))
    var replays: Vector[ReplayRecord] = Vector(replayRecord(ReplayId("replay-1"), BattleId("battle-1")))
    var loadedReplay: Option[ReplayRecord] = Some(replayRecord(ReplayId("replay-1"), BattleId("battle-1")))
    var commentResult: Either[ReplayCommentError, ReplayCommentRecord] = Right(replayComment())
    var comments: Vector[ReplayCommentRecord] = Vector(replayComment())

    override def record(command: ReplayRecordCommand): IO[Either[ReplayRecordError, ReplayRecord]] =
      recordCommands = recordCommands :+ command
      IO.pure(recordResult)

    override def list(limit: Int): IO[Vector[ReplayRecord]] =
      listLimits = listLimits :+ limit
      IO.pure(replays)

    override def load(replayId: ReplayId): IO[Option[ReplayRecord]] =
      loadCalls = loadCalls :+ replayId
      IO.pure(loadedReplay)

    override def addComment(command: ReplayCommentCommand): IO[Either[ReplayCommentError, ReplayCommentRecord]] =
      commentCommands = commentCommands :+ command
      IO.pure(commentResult)

    override def listComments(replayId: ReplayId, limit: Int): IO[Vector[ReplayCommentRecord]] =
      listCommentCalls = listCommentCalls :+ ((replayId, limit))
      IO.pure(comments)

  private def replayRecord(replayId: ReplayId, battleId: BattleId): ReplayRecord =
    ReplayRecord(
      replayId = replayId,
      battleId = battleId,
      handle = PlayerHandle("Alice"),
      displayName = DisplayName("Alice"),
      finishedAt = EpochMillis(3000),
      finishedAtLabel = "just now",
      title = ReplayTitle.fromWire("Replay title"),
      modeLabel = "Arena Mode",
      resultLabel = "Victory",
      mapLabel = "Island",
      highlightLine = "Alice won",
      coverLabel = "Top 1",
      playersLine = "Alice vs Bob",
      timelineHint = "30s",
      score = Score(88),
      placement = Some(BattlePlacement.unsafe(1)),
      ratingBefore = Some(Rating(1200)),
      ratingDelta = Some(RatingDelta(12)),
      ratingAfter = Some(Rating(1212)),
      durationMs = DurationMillis(30000),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      thumbnailDataUrl = None,
      currentLoadout = Some("rifle"),
      frameCount = ReplayFrameCount.fromWire(1),
      playbackAvailability = ReplayPlaybackAvailability.Available,
      framesJson = ReplayFramesJson.fromNormalized("""[{"tick":1}]"""),
      settlements = Vector.empty
    )

  private def replayComment(): ReplayCommentRecord =
    ReplayCommentRecord(
      id = ReplayCommentId("comment-1"),
      replayId = ReplayId("replay-1"),
      authorHandle = PlayerHandle("Bob"),
      body = "Good fight.",
      createdAt = EpochMillis(4000)
    )

private[contract] object BotProfileHttp4sRouteContractTest:
  def run(): Unit =
    getRendersProfilePathAliases()
    unsupportedMethodIsRejectedWithoutServiceCall()

  private def getRendersProfilePathAliases(): Unit =
    Vector("/api/bots/profiles", "/api/bot/profiles", "/bots/profiles", "/bot/profiles").foreach { path =>
      val service = RecordingBotProfileService()
      val response = RouteContractSupport.runRoute(
        BotProfileHttp4sRoutes.routes(service),
        Request[IO](method = Method.GET, uri = org.http4s.Uri.unsafeFromString(path))
      )

      ContractAssertions.assertEquals(s"$path bot profile status", response.status, 200)
      ContractAssertions.assertContains(s"$path bot profile wrapper", response.body, """"profiles":[""")
      ContractAssertions.assertContains(s"$path bot profile id", response.body, """"botId":"bot-1"""")
      ContractAssertions.assertContains(s"$path bot profile handle", response.body, """"handle":"cpu-sable"""")
      ContractAssertions.assertContains(s"$path bot profile tone", response.body, """"profileTone":"aggressive"""")
      ContractAssertions.assertContains(s"$path bot profile skin", response.body, """"textureKey":"hero-soldier"""")
      ContractAssertions.assertEquals(s"$path bot profile list call", service.listCalls, 1)
    }

  private def unsupportedMethodIsRejectedWithoutServiceCall(): Unit =
    val service = RecordingBotProfileService()
    val response = RouteContractSupport.runRoute(
      BotProfileHttp4sRoutes.routes(service),
      Request[IO](method = Method.POST, uri = uri"/api/bots/profiles").withEntity("{}")
    )

    ContractAssertions.assertEquals("bot profile unsupported method status", response.status, 405)
    ContractAssertions.assertContains("bot profile unsupported method code", response.body, """"code":"method_not_allowed"""")
    ContractAssertions.assertEquals("bot profile unsupported method no service call", service.listCalls, 0)

  private final class RecordingBotProfileService extends BotProfileService:
    var listCalls: Int = 0
    var records: Vector[BotProfileRecord] = Vector(botProfileRecord())

    override def list(): IO[Vector[BotProfileRecord]] =
      listCalls += 1
      IO.pure(records)

  private def botProfileRecord(): BotProfileRecord =
    BotProfileRecord(
      botId = BotId("bot-1"),
      handle = PlayerHandle("cpu-sable"),
      displayName = DisplayName("Sable"),
      initialRating = BotInitialRating(1010),
      profileTone = BotProfileTone.Aggressive,
      strategyLabel = BotStrategyLabel("Pressure duelist"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("soldier"),
        textureKey = BotTextureKey("hero-soldier"),
        label = BotSkinLabel("Soldier")
      ),
      profileOrder = BotProfileOrder(0)
    )
