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
import services.battle.routes.BattleAPIMessageServices
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

private[contract] object BattleHttpRouteContractSupport:
  def routes(
    queueService: BattleQueueService = NoopBattleQueueService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService = AllowBattleQueueJoinAuthorizationService,
    resultService: BattleResultService = NoopBattleResultService,
    stateService: BattleStateService = NoopBattleStateService
  ): HttpRoutes[IO] =
    BattleHttp4sRoutes.routes(
      BattleAPIMessageServices(
        queueService = queueService,
        joinAuthorizationService = joinAuthorizationService,
        resultService = resultService,
        stateService = stateService
      )
    )

  private object AllowBattleQueueJoinAuthorizationService extends BattleQueueJoinAuthorizationService:
    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] =
      Right(())

  private object NoopBattleQueueService extends BattleQueueService:
    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      BattleContractFixtures.queueSnapshot(command.handle, command.rating, command.avatar, command.skin)

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      Left(BattleQueueStatusError.TicketNotFound)

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      BattleQueueLeaveOutcome.TicketNotFound

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      Left(BattleRoomError.RoomNotFound)

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      Left(BattleRoomError.RoomNotFound)

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      ()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      None

  private object NoopBattleStateService extends BattleStateService:
    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      Left(BattleStateReadError.BattleNotFound)

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] =
      Left(BattleCommandSubmitError.BattleNotFound)

  private object NoopBattleResultService extends BattleResultService:
    override def record(command: BattleResultRecordCommand): Either[BattleResultRecordError, BattleResultRecord] =
      Left(BattleResultRecordError.InvalidHandle)

    override def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord] =
      Vector.empty

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
          """{"handle":"Alice","sessionToken":"session-alice","queueRequestId":"queue-request-1","rating":1200,"avatar":"fox","skin":"soldier"}"""
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
    ContractAssertions.assertEquals("queue join command request id", queueService.joinCommands.head.queueRequestId, Some(QueueRequestId("queue-request-1")))

  private def statusRequiresTicketId(): Unit =
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = RecordingBattleQueueService()),
      Request[IO](method = Method.POST, uri = uri"/api/battlequeuestatus").withEntity("{}")
    )

    ContractAssertions.assertEquals("queue status missing ticket status", response.status, 400)
    ContractAssertions.assertContains("queue status missing ticket message", response.body, "ticketId is required.")

  private def statusRendersSnapshot(): Unit =
    val service = RecordingBattleQueueService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battlequeuestatus").withEntity("""{"ticketId":"ticket-1"}""")
    )

    ContractAssertions.assertEquals("queue status response status", response.status, 200)
    ContractAssertions.assertContains("queue status ticket", response.body, """"ticketId":"ticket-1"""")
    ContractAssertions.assertContains("queue status player", response.body, """"playerId":"player-1"""")
    ContractAssertions.assertEquals("queue status ticket id", service.statusTicketIds, Vector(TicketId("ticket-1")))

  private final class RecordingBattleQueueJoinAuthorizationService extends BattleQueueJoinAuthorizationService:
    var commands: Vector[BattleQueueJoinCommand] = Vector.empty
    var result: Either[BattleQueueJoinAuthorizationError, Unit] = Right(())

    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] =
      commands = commands :+ command
      result

  private final class RecordingBattleQueueService extends BattleQueueService:
    var joinCommands: Vector[BattleQueueJoinCommand] = Vector.empty
    var statusTicketIds: Vector[TicketId] = Vector.empty

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      joinCommands = joinCommands :+ command
      BattleContractFixtures.queueSnapshot(command.handle, command.rating, command.avatar, command.skin)

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      statusTicketIds = statusTicketIds :+ ticketId
      Right(BattleContractFixtures.queueSnapshot(PlayerHandle("Alice"), Some(Rating(1200)), Some("fox"), Some("soldier")))

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      BattleQueueLeaveOutcome.LeftQueue

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      Left(BattleRoomError.RoomNotFound)

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      Left(BattleRoomError.RoomNotFound)

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      ()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      None

private[contract] object BattleRoomHttp4sRouteContractTest:
  def run(): Unit =
    snapshotRequiresRoomId()
    snapshotRendersRoom()
    heartbeatParsesPathAndBody()

  private def snapshotRequiresRoomId(): Unit =
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = RecordingBattleRoomQueueService()),
      Request[IO](method = Method.POST, uri = uri"/api/battleroomsnapshot").withEntity("{}")
    )

    ContractAssertions.assertEquals("room snapshot missing room status", response.status, 400)
    ContractAssertions.assertContains("room snapshot missing room message", response.body, "roomId is required.")

  private def snapshotRendersRoom(): Unit =
    val service = RecordingBattleRoomQueueService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battleroomsnapshot").withEntity("""{"roomId":"room-1"}""")
    )

    ContractAssertions.assertEquals("room snapshot status", response.status, 200)
    ContractAssertions.assertContains("room snapshot id", response.body, """"roomId":"room-1"""")
    ContractAssertions.assertContains("room snapshot phase", response.body, """"phase":"waiting"""")
    ContractAssertions.assertEquals("room snapshot requested id", service.snapshotRoomIds, Vector(RoomId("room-1")))

  private def heartbeatParsesPathAndBody(): Unit =
    val service = RecordingBattleRoomQueueService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(queueService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battleroomheartbeat")
        .withEntity("""{"roomId":"room-1","ticketId":"ticket-1","handle":"Alice"}""")
    )

    ContractAssertions.assertEquals("room heartbeat status", response.status, 200)
    ContractAssertions.assertContains("room heartbeat id", response.body, """"roomId":"room-1"""")
    ContractAssertions.assertEquals("room heartbeat command count", service.heartbeatCommands.length, 1)
    ContractAssertions.assertEquals("room heartbeat command room", service.heartbeatCommands.head.roomId, Some(RoomId("room-1")))
    ContractAssertions.assertEquals("room heartbeat command ticket", service.heartbeatCommands.head.ticketId, Some(TicketId("ticket-1")))
    ContractAssertions.assertEquals("room heartbeat command handle", service.heartbeatCommands.head.handle, Some(PlayerHandle("Alice")))

  private final class RecordingBattleRoomQueueService extends BattleQueueService:
    var snapshotRoomIds: Vector[RoomId] = Vector.empty
    var heartbeatCommands: Vector[RealtimeRoomHeartbeatCommand] = Vector.empty

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      BattleContractFixtures.queueSnapshot(command.handle, command.rating, command.avatar, command.skin)

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      Right(BattleContractFixtures.queueSnapshot(PlayerHandle("Alice"), Some(Rating(1200)), Some("fox"), Some("soldier")))

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      BattleQueueLeaveOutcome.LeftQueue

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      snapshotRoomIds = snapshotRoomIds :+ roomId
      Right(BattleContractFixtures.roomSnapshot(roomId))

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      heartbeatCommands = heartbeatCommands :+ request
      Right(BattleContractFixtures.roomSnapshot(request.roomId.getOrElse(RoomId("room-1"))))

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      ()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      None

private[contract] object BattleStateHttp4sRouteContractTest:
  def run(): Unit =
    readRendersState()
    readRequiresBattleId()

  private def readRendersState(): Unit =
    val service = RecordingBattleStateService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(stateService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battlestateread").withEntity("""{"battleId":"battle-1"}""")
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
      Request[IO](method = Method.POST, uri = uri"/api/battlestateread").withEntity("{}")
    )

    ContractAssertions.assertEquals("battle state missing id status", response.status, 400)
    ContractAssertions.assertContains("battle state missing id message", response.body, "battleId is required.")

  private final class RecordingBattleStateService extends BattleStateService:
    var requestedBattleIds: Vector[BattleId] = Vector.empty

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      requestedBattleIds = requestedBattleIds :+ battleId
      Right(BattleContractFixtures.aggregateState(battleId))

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] =
      Left(BattleCommandSubmitError.BattleNotFound)

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
    s"""{"battleId":"battle-1","playerId":"player-1",$ticketField"clientTick":15,"clientCommandSeq":16,"movement":{"x":1.0,"y":0.0},"aim":{"x":0.0,"y":1.0},"primaryHeld":true,"sprint":false,"reloadPressed":false,"castDash":false,"castBlink":false,"castFreeze":false,"pointerWorld":{"x":12.0,"y":18.0},"switchWeaponDirection":1,"switchWeaponIndex":0}"""

  private final class RecordingBattleCommandStateService extends BattleStateService:
    var acceptedRequests: Vector[BattleCommandRequest] = Vector.empty

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      Right(BattleContractFixtures.aggregateState(battleId))

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] =
      acceptedRequests = acceptedRequests :+ request
      Right(
        BattleCommandAccepted(
          battleId = request.battleId,
          acceptedTick = request.clientTick,
          acceptedCommandSeq = request.clientCommandSeq,
          serverTime = EpochMillis(2200),
          commandStatus = BattleCommandStatus.Applied,
          commandReason = None,
          outcomes = Vector.empty
        )
      )

private[contract] object BattleResultHttp4sRouteContractTest:
  def run(): Unit =
    listRendersRecordsAndPassesFilters()
    postParsesRecordAndRendersCreated()

  private def listRendersRecordsAndPassesFilters(): Unit =
    val service = RecordingBattleResultService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(resultService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battleresultlist")
        .withEntity("""{"handle":"Alice","battleId":"battle-1","limit":5}""")
    )

    ContractAssertions.assertEquals("battle result list status", response.status, 200)
    ContractAssertions.assertContains("battle result list wrapper", response.body, """"results":[""")
    ContractAssertions.assertContains("battle result list result id", response.body, """"resultId":"battle-1:alice"""")
    ContractAssertions.assertEquals("battle result list filters", service.listCalls, Vector((Some(PlayerHandle("Alice")), Some(BattleId("battle-1")), 5)))

  private def postParsesRecordAndRendersCreated(): Unit =
    val service = RecordingBattleResultService()
    val response = RouteContractSupport.runRoute(
      BattleHttpRouteContractSupport.routes(resultService = service),
      Request[IO](method = Method.POST, uri = uri"/api/battleresultrecord")
        .withEntity(resultRecordBody)
    )

    ContractAssertions.assertEquals("battle result post status", response.status, 200)
    ContractAssertions.assertContains("battle result post id", response.body, """"resultId":"battle-1:alice"""")
    ContractAssertions.assertContains("battle result post score", response.body, """"score":42""")
    ContractAssertions.assertEquals("battle result record count", service.recordCommands.length, 1)
    ContractAssertions.assertEquals("battle result record handle", service.recordCommands.head.handle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("battle result record battle", service.recordCommands.head.battleId, BattleId("battle-1"))

  private val resultRecordBody: String =
    """{"battleId":"battle-1","handle":"Alice","displayName":"Alice","finishedAt":3000,"finishedAtLabel":"now","durationMs":120000,"score":42,"placement":1,"aliveAtEnd":true,"ratingBefore":1200,"ratingDelta":15,"ratingAfter":1215,"resultLabel":"Victory","modeLabel":"Arena","mapLabel":"Island","highlightLine":"Alice won","playersLine":"Alice","timelineHint":"2m","currentLoadout":"Pistol"}"""

  private final class RecordingBattleResultService extends BattleResultService:
    var recordCommands: Vector[BattleResultRecordCommand] = Vector.empty
    var listCalls: Vector[(Option[PlayerHandle], Option[BattleId], Int)] = Vector.empty

    override def record(command: BattleResultRecordCommand): Either[BattleResultRecordError, BattleResultRecord] =
      recordCommands = recordCommands :+ command
      Right(BattleContractFixtures.resultRecord(command.battleId, command.handle))

    override def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord] =
      listCalls = listCalls :+ ((handle, battleId, limit))
      Vector(BattleContractFixtures.resultRecord(BattleId("battle-1"), PlayerHandle("Alice")))
