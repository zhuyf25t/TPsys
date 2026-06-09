package route.contract

import java.lang.reflect.{InvocationHandler, Method as JavaMethod, Proxy}
import java.nio.file.{Files, Path, Paths}
import java.security.SecureRandom
import java.sql.Connection

import cats.effect.{IO, Resource}
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
import services.battle.routes.BattleAPIRuntimeContext
import services.battle.microservices.actors.objects.player.{BattleAvatarKey, BattleSkinKey, Rating}
import services.battle.microservices.session.objects.command.{
  BattleCommandAccepted,
  BattleCommandRequest,
  BattleCommandStatus
}
import services.battle.microservices.queue.objects.queue.*
import services.battle.objects.*
import services.bots.objects.*
import services.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository}
import services.forum.database.{FileForumRepository, InMemoryForumRepository}
import services.forum.objects.*
import services.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository}
import services.governance.objects.*
import services.identity.database.{FileIdentityAccountRepository, InMemoryIdentityAccountRepository}
import services.identity.objects.IdentityAccountSummary
import services.identity.objects.{IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
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
import services.battle.microservices.results.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjector}
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

private[contract] object BattleHttpRouteContractSupport:
  def routes(
    queueService: BattleQueueService = NoopBattleQueueService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService = AllowBattleQueueJoinAuthorizationService,
    stateService: BattleStateService = NoopBattleStateService,
    identityService: IdentityService = AllowIdentityService
  ): HttpRoutes[IO] =
    BattleHttp4sRoutes.routes(
      BattleAPIRuntimeContext(
        queueService = queueService,
        joinAuthorizationService = joinAuthorizationService,
        stateService = stateService
      ),
      identityService = identityService,
      connectionResource = Resource.pure(dummyConnection)
    )

  private def dummyConnection: Connection =
    Proxy
      .newProxyInstance(
        classOf[Connection].getClassLoader,
        Array(classOf[Connection]),
        (_: AnyRef, method: JavaMethod, _: Array[AnyRef]) =>
          method.getName match
            case "close" => ()
            case "isClosed" => java.lang.Boolean.FALSE
            case "toString" => "BattleHttpRouteContractSupport.dummyConnection"
            case "isWrapperFor" => java.lang.Boolean.FALSE
            case "unwrap" => throw UnsupportedOperationException("No contract JDBC connection configured.")
            case _ => throw UnsupportedOperationException("No contract JDBC connection configured.")
      )
      .asInstanceOf[Connection]

  private object AllowBattleQueueJoinAuthorizationService extends BattleQueueJoinAuthorizationService:
    override def authorize(command: BattleQueueJoinCommand): IO[Either[BattleQueueJoinAuthorizationError, Unit]] =
      IO.pure(Right(()))

  private object NoopBattleQueueService extends BattleQueueService:
    override def join(command: BattleQueueJoinCommand): IO[BattleQueueSnapshot] =
      IO.pure(BattleContractFixtures.queueSnapshot(command.handle, command.rating, command.avatar, command.skin))

    override def status(ticketId: TicketId): IO[Either[BattleQueueStatusError, BattleQueueSnapshot]] =
      IO.pure(Left(BattleQueueStatusError.TicketNotFound))

    override def leave(ticketId: TicketId): IO[BattleQueueLeaveOutcome] =
      IO.pure(BattleQueueLeaveOutcome.TicketNotFound)

    override def roomSnapshot(roomId: RoomId): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
      IO.pure(Left(BattleRoomError.RoomNotFound))

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
      IO.pure(Left(BattleRoomError.RoomNotFound))

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit] =
      IO.unit

    override def activeBattleSession(battleId: BattleId): IO[Option[BattleSessionSeed]] =
      IO.pure(None)

  private object NoopBattleStateService extends BattleStateService:
    override def currentState(battleId: BattleId): IO[Either[BattleStateReadError, BattleAggregateState]] =
      IO.pure(Left(BattleStateReadError.BattleNotFound))

    override def acceptCommand(request: BattleCommandRequest): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]] =
      IO.pure(Left(BattleCommandSubmitError.BattleNotFound))

  private object AllowIdentityService extends IdentityService:
    override def register(command: IdentityRegistrationCommand): IO[Either[IdentityRegistrationError, IdentityAccount]] =
      IO.pure(Right(identityAccount(command.handle, command.skinId, None)))

    override def issueSession(command: IdentitySessionCommand): IO[Either[IdentitySessionError, IdentityAccount]] =
      IO.pure(Right(identityAccount(command.handle, SkinId.Blue, Some(SessionToken(s"session-${command.handle.key}")))))

    override def current(sessionToken: Option[SessionToken]): IO[Either[IdentityCurrentSessionError, IdentityAccount]] =
      IO.pure(
        sessionToken match
          case Some(token) =>
            Right(identityAccount(PlayerHandle("tester"), SkinId.Blue, Some(token)))
          case None =>
            Left(IdentityCurrentSessionError.MissingSession)
      )

    override def listActiveAccounts(): IO[Vector[IdentityAccountSummary]] =
      IO.pure(Vector.empty)

  private def identityAccount(
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

private[contract] object BattleQueueHttp4sRouteContractTest:
  def run(): Unit =
    joinParsesCommandAndRendersSnapshot()
    statusRequiresTicketId()
    statusRendersSnapshot()

  private def joinParsesCommandAndRendersSnapshot(): Unit =
    val queueService = RecordingBattleQueueService()
    val authorizationService = RecordingBattleQueueJoinAuthorizationService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = queueService, joinAuthorizationService = authorizationService),
      Request[IO](method = Method.POST, uri = uri"/api/battlequeuejoin")
        .withEntity(
          """{"userToken":"session-tester","handle":"Alice","sessionToken":"session-alice","queueRequestId":"queue-request-1","rating":1200,"avatar":"fox","skin":"soldier"}"""
        )
    )

    ContractAssertions.assertEquals("queue join status", response.status, 200)
    ContractAssertions.assertContains("queue join ticket", response.body, """"ticketId":"ticket-1"""")
    ContractAssertions.assertContains("queue join phase", response.body, """"phase":"waiting"""")
    ContractAssertions.assertContains("queue join rating", response.body, """"rating":1200""")
    ContractAssertions.assertEquals("queue authorize command count", authorizationService.commands.length, 1)
    ContractAssertions.assertEquals("queue join command count", queueService.joinCommands.length, 1)
    ContractAssertions.assertEquals("queue join command handle", queueService.joinCommands.head.handle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("queue join command session", queueService.joinCommands.head.sessionToken, SessionToken("session-alice"))
    ContractAssertions.assertEquals("queue join command default mode", queueService.joinCommands.head.battleMode, BattleMode.Default)
    ContractAssertions.assertEquals("queue join command request id", queueService.joinCommands.head.queueRequestId, Some(QueueRequestId("queue-request-1")))

  private def statusRequiresTicketId(): Unit =
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = RecordingBattleQueueService()),
      Request[IO](method = Method.POST, uri = uri"/api/battlequeuestatus").withEntity("""{"userToken":"session-tester"}""")
    )

    ContractAssertions.assertEquals("queue status missing ticket status", response.status, 400)
    ContractAssertions.assertContains("queue status missing ticket message", response.body, "ticketId is required.")

  private def statusRendersSnapshot(): Unit =
    val service = RecordingBattleQueueService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battlequeuestatus").withEntity("""{"userToken":"session-tester","ticketId":"ticket-1"}""")
    )

    ContractAssertions.assertEquals("queue status response status", response.status, 200)
    ContractAssertions.assertContains("queue status ticket", response.body, """"ticketId":"ticket-1"""")
    ContractAssertions.assertContains("queue status player", response.body, """"playerId":"player-1"""")
    ContractAssertions.assertEquals("queue status ticket id", service.statusTicketIds, Vector(TicketId("ticket-1")))

  private final class RecordingBattleQueueJoinAuthorizationService extends BattleQueueJoinAuthorizationService:
    var commands: Vector[BattleQueueJoinCommand] = Vector.empty
    var result: Either[BattleQueueJoinAuthorizationError, Unit] = Right(())

    override def authorize(command: BattleQueueJoinCommand): IO[Either[BattleQueueJoinAuthorizationError, Unit]] =
      commands = commands :+ command
      IO.pure(result)

  private final class RecordingBattleQueueService extends BattleQueueService:
    var joinCommands: Vector[BattleQueueJoinCommand] = Vector.empty
    var statusTicketIds: Vector[TicketId] = Vector.empty

    override def join(command: BattleQueueJoinCommand): IO[BattleQueueSnapshot] =
      joinCommands = joinCommands :+ command
      IO.pure(BattleContractFixtures.queueSnapshot(command.handle, command.rating, command.avatar, command.skin))

    override def status(ticketId: TicketId): IO[Either[BattleQueueStatusError, BattleQueueSnapshot]] =
      statusTicketIds = statusTicketIds :+ ticketId
      IO.pure(Right(
        BattleContractFixtures.queueSnapshot(
          PlayerHandle("Alice"),
          Some(Rating(1200)),
          BattleAvatarKey.fromWire("fox"),
          BattleSkinKey.fromWire("soldier")
        )
      ))

    override def leave(ticketId: TicketId): IO[BattleQueueLeaveOutcome] =
      IO.pure(BattleQueueLeaveOutcome.LeftQueue)

    override def roomSnapshot(roomId: RoomId): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
      IO.pure(Left(BattleRoomError.RoomNotFound))

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
      IO.pure(Left(BattleRoomError.RoomNotFound))

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit] =
      IO.unit

    override def activeBattleSession(battleId: BattleId): IO[Option[BattleSessionSeed]] =
      IO.pure(None)

private[contract] object BattleRoomHttp4sRouteContractTest:
  def run(): Unit =
    snapshotRequiresRoomId()
    snapshotRendersRoom()
    heartbeatParsesPathAndBody()

  private def snapshotRequiresRoomId(): Unit =
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = RecordingBattleRoomQueueService()),
      Request[IO](method = Method.POST, uri = uri"/api/battleroomsnapshot").withEntity("""{"userToken":"session-tester"}""")
    )

    ContractAssertions.assertEquals("room snapshot missing room status", response.status, 400)
    ContractAssertions.assertContains("room snapshot missing room message", response.body, "roomId is required.")

  private def snapshotRendersRoom(): Unit =
    val service = RecordingBattleRoomQueueService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battleroomsnapshot").withEntity("""{"userToken":"session-tester","roomId":"room-1"}""")
    )

    ContractAssertions.assertEquals("room snapshot status", response.status, 200)
    ContractAssertions.assertContains("room snapshot id", response.body, """"roomId":"room-1"""")
    ContractAssertions.assertContains("room snapshot phase", response.body, """"phase":"waiting"""")
    ContractAssertions.assertContains("room snapshot start paused", response.body, """"startPaused":false""")
    ContractAssertions.assertContains("room snapshot chat messages", response.body, """"chatMessages":[]""")
    ContractAssertions.assertEquals("room snapshot requested id", service.snapshotRoomIds, Vector(RoomId("room-1")))

  private def heartbeatParsesPathAndBody(): Unit =
    val service = RecordingBattleRoomQueueService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battleroomheartbeat")
        .withEntity("""{"userToken":"session-tester","roomId":"room-1","ticketId":"ticket-1","handle":"Alice","startPaused":true,"chatMessage":"Ready check"}""")
    )

    ContractAssertions.assertEquals("room heartbeat status", response.status, 200)
    ContractAssertions.assertContains("room heartbeat id", response.body, """"roomId":"room-1"""")
    ContractAssertions.assertEquals("room heartbeat command count", service.heartbeatCommands.length, 1)
    ContractAssertions.assertEquals("room heartbeat command room", service.heartbeatCommands.head.roomId, Some(RoomId("room-1")))
    ContractAssertions.assertEquals("room heartbeat command ticket", service.heartbeatCommands.head.ticketId, Some(TicketId("ticket-1")))
    ContractAssertions.assertEquals("room heartbeat command handle", service.heartbeatCommands.head.handle, Some(PlayerHandle("Alice")))
    ContractAssertions.assertEquals("room heartbeat command pause", service.heartbeatCommands.head.startPaused, Some(true))
    ContractAssertions.assertEquals("room heartbeat command chat", service.heartbeatCommands.head.chatMessage.map(_.value), Some("Ready check"))

  private final class RecordingBattleRoomQueueService extends BattleQueueService:
    var snapshotRoomIds: Vector[RoomId] = Vector.empty
    var heartbeatCommands: Vector[RealtimeRoomHeartbeatCommand] = Vector.empty

    override def join(command: BattleQueueJoinCommand): IO[BattleQueueSnapshot] =
      IO.pure(BattleContractFixtures.queueSnapshot(command.handle, command.rating, command.avatar, command.skin))

    override def status(ticketId: TicketId): IO[Either[BattleQueueStatusError, BattleQueueSnapshot]] =
      IO.pure(Right(
        BattleContractFixtures.queueSnapshot(
          PlayerHandle("Alice"),
          Some(Rating(1200)),
          BattleAvatarKey.fromWire("fox"),
          BattleSkinKey.fromWire("soldier")
        )
      ))

    override def leave(ticketId: TicketId): IO[BattleQueueLeaveOutcome] =
      IO.pure(BattleQueueLeaveOutcome.LeftQueue)

    override def roomSnapshot(roomId: RoomId): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
      snapshotRoomIds = snapshotRoomIds :+ roomId
      IO.pure(Right(BattleContractFixtures.roomSnapshot(roomId)))

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
      heartbeatCommands = heartbeatCommands :+ request
      IO.pure(Right(BattleContractFixtures.roomSnapshot(request.roomId.getOrElse(RoomId("room-1")))))

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit] =
      IO.unit

    override def activeBattleSession(battleId: BattleId): IO[Option[BattleSessionSeed]] =
      IO.pure(None)

private[contract] object BattleStateHttp4sRouteContractTest:
  def run(): Unit =
    readRendersState()
    readRequiresBattleId()

  private def readRendersState(): Unit =
    val service = RecordingBattleStateService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(stateService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battlestateread").withEntity("""{"userToken":"session-tester","battleId":"battle-1"}""")
    )

    ContractAssertions.assertEquals("battle state read status", response.status, 200)
    ContractAssertions.assertContains("battle state id", response.body, """"battleId":"battle-1"""")
    ContractAssertions.assertContains("battle state room", response.body, """"roomId":"room-1"""")
    ContractAssertions.assertContains("battle state phase", response.body, """"phase":"active"""")
    ContractAssertions.assertContains("battle state players", response.body, """"players":[]""")
    ContractAssertions.assertEquals("battle state requested id", service.requestedBattleIds, Vector(BattleId("battle-1")))

  private def readRequiresBattleId(): Unit =
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(stateService = RecordingBattleStateService()),
      Request[IO](method = Method.POST, uri = uri"/api/battlestateread").withEntity("""{"userToken":"session-tester"}""")
    )

    ContractAssertions.assertEquals("battle state missing id status", response.status, 400)
    ContractAssertions.assertContains("battle state missing id message", response.body, "battleId is required.")

  private final class RecordingBattleStateService extends BattleStateService:
    var requestedBattleIds: Vector[BattleId] = Vector.empty

    override def currentState(battleId: BattleId): IO[Either[BattleStateReadError, BattleAggregateState]] =
      requestedBattleIds = requestedBattleIds :+ battleId
      IO.pure(Right(BattleContractFixtures.aggregateState(battleId)))

    override def acceptCommand(request: BattleCommandRequest): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]] =
      IO.pure(Left(BattleCommandSubmitError.BattleNotFound))

private[contract] object BattleCommandHttp4sRouteContractTest:
  def run(): Unit =
    commandParsesAndRendersAccepted()
    commandRequiresTicket()

  private def commandParsesAndRendersAccepted(): Unit =
    val service = RecordingBattleCommandStateService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(stateService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battlecommand")
        .withEntity(commandBody(ticketId = Some("ticket-1")))
    )

    ContractAssertions.assertEquals("battle command status", response.status, 200)
    ContractAssertions.assertContains("battle command id", response.body, """"battleId":"battle-1"""")
    ContractAssertions.assertContains("battle command accepted tick", response.body, """"acceptedTick":15""")
    ContractAssertions.assertContains("battle command status body", response.body, """"commandStatus":"applied"""")
    ContractAssertions.assertEquals("battle command request count", service.acceptedRequests.length, 1)
    ContractAssertions.assertEquals("battle command ticket", service.acceptedRequests.head.ticketId, TicketId("ticket-1"))
    ContractAssertions.assertEquals("battle command player", service.acceptedRequests.head.playerId, PlayerId("player-1"))

  private def commandRequiresTicket(): Unit =
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(stateService = RecordingBattleCommandStateService()),
      Request[IO](method = Method.POST, uri = uri"/api/battlecommand")
        .withEntity(commandBody(ticketId = None))
    )

    ContractAssertions.assertEquals("battle command missing ticket status", response.status, 403)
    ContractAssertions.assertContains("battle command missing ticket message", response.body, "command_not_authorized")

  private def commandBody(ticketId: Option[String]): String =
    val ticketField = ticketId.map(value => s""""ticketId":"$value",""").getOrElse("")
    s"""{"userToken":"session-tester","battleId":"battle-1","playerId":"player-1",$ticketField"clientTick":15,"clientCommandSeq":16,"movement":{"x":1.0,"y":0.0},"aim":{"x":0.0,"y":1.0},"primaryHeld":true,"sprint":false,"reloadPressed":false,"castDash":false,"castBlink":false,"castFreeze":false,"pointerWorld":{"x":12.0,"y":18.0},"switchWeaponDirection":1,"switchWeaponIndex":0}"""

  private final class RecordingBattleCommandStateService extends BattleStateService:
    var acceptedRequests: Vector[BattleCommandRequest] = Vector.empty

    override def currentState(battleId: BattleId): IO[Either[BattleStateReadError, BattleAggregateState]] =
      IO.pure(Right(BattleContractFixtures.aggregateState(battleId)))

    override def acceptCommand(request: BattleCommandRequest): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]] =
      acceptedRequests = acceptedRequests :+ request
      IO.pure(Right(
        BattleCommandAccepted(
          battleId = request.battleId,
          acceptedTick = request.clientTick,
          acceptedCommandSeq = request.clientCommandSeq,
          serverTime = EpochMillis(2200),
          commandStatus = BattleCommandStatus.Applied,
          commandReason = None,
          outcomes = Vector.empty
        )
      ))

private[contract] object BattleResultHttp4sRouteContractTest:
  def run(): Unit =
    ()
