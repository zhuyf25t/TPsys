package slaydemo.backend.battle.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

object BattleJoinRouteContractTest {
  private val ValidJoinJson: String =
    """{"handle":"alice","sessionToken":"session-alice","queueRequestId":"join-route-1","rating":1200,"avatar":"blue","skin":"pilot"}"""

  def main(args: Array[String]): Unit = {
    validJoinAuthorizesAndReturnsSnapshot()
    invalidHandleIsRejectedBeforeAuthorization()
    missingSessionIsUnauthorizedBeforeQueue()
    invalidSessionIsUnauthorized()
    handleMismatchIsForbidden()

    println("Battle join route contract checks passed")
  }

  private def validJoinAuthorizesAndReturnsSnapshot(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))

    withJoinServer(queueService, authService) { uri =>
      val response = postJson(uri, ValidJoinJson)

      assertEquals("valid join status", response.status, 200)
      assertContains("valid join ticket", response.body, """"ticketId":"ticket-alice"""")
      assertEquals("valid join auth count", authService.commands.length, 1)
      assertEquals("valid join queue count", queueService.commands.length, 1)
      val command = queueService.commands.head
      assertEquals("valid join handle", command.handle, PlayerHandle("alice"))
      assertEquals("valid join session", command.sessionToken, SessionToken("session-alice"))
      assertEquals("valid join request id", command.queueRequestId, Some(QueueRequestId("join-route-1")))
      assertEquals("valid join rating", command.rating, Some(Rating(1200)))
      assertEquals("valid join avatar", command.avatar, Some("blue"))
      assertEquals("valid join skin", command.skin, Some("pilot"))
    }
  }

  private def invalidHandleIsRejectedBeforeAuthorization(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))

    withJoinServer(queueService, authService) { uri =>
      val response = postJson(uri, ValidJoinJson.replace("\"handle\":\"alice\"", "\"handle\":\"visitor\""))

      assertEquals("invalid handle status", response.status, 400)
      assertContains("invalid handle code", response.body, """"code":"invalid_handle"""")
      assertEquals("invalid handle auth count", authService.commands.length, 0)
      assertEquals("invalid handle queue count", queueService.commands.length, 0)
    }
  }

  private def missingSessionIsUnauthorizedBeforeQueue(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Right(()))

    withJoinServer(queueService, authService) { uri =>
      val response = postJson(uri, ValidJoinJson.replace("\"sessionToken\":\"session-alice\",", ""))

      assertEquals("missing session status", response.status, 401)
      assertContains("missing session code", response.body, """"code":"missing_session"""")
      assertEquals("missing session auth count", authService.commands.length, 0)
      assertEquals("missing session queue count", queueService.commands.length, 0)
    }
  }

  private def invalidSessionIsUnauthorized(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Left(BattleQueueJoinAuthorizationError.InvalidSession))

    withJoinServer(queueService, authService) { uri =>
      val response = postJson(uri, ValidJoinJson)

      assertEquals("invalid session status", response.status, 401)
      assertContains("invalid session code", response.body, """"code":"invalid_session"""")
      assertEquals("invalid session auth count", authService.commands.length, 1)
      assertEquals("invalid session queue count", queueService.commands.length, 0)
    }
  }

  private def handleMismatchIsForbidden(): Unit = {
    val queueService = RecordingBattleQueueService()
    val authService = RecordingJoinAuthorizationService(Left(BattleQueueJoinAuthorizationError.HandleMismatch))

    withJoinServer(queueService, authService) { uri =>
      val response = postJson(uri, ValidJoinJson)

      assertEquals("handle mismatch status", response.status, 403)
      assertContains("handle mismatch code", response.body, """"code":"identity_mismatch"""")
      assertEquals("handle mismatch auth count", authService.commands.length, 1)
      assertEquals("handle mismatch queue count", queueService.commands.length, 0)
    }
  }

  private def withJoinServer[A](
    queueService: RecordingBattleQueueService,
    authService: RecordingJoinAuthorizationService
  )(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = BattleRoutes(queueService, UnusedBattleStateService, authService)
    server.createContext("/api/battle/queue/join", exchange => routes.join(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/api/battle/queue/join"))
    finally server.stop(0)
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

  private final class RecordingBattleQueueService extends BattleQueueService {
    private var recordedCommands: Vector[BattleQueueJoinCommand] = Vector.empty

    def commands: Vector[BattleQueueJoinCommand] =
      recordedCommands

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot = {
      recordedCommands = recordedCommands :+ command
      BattleQueueSnapshot(
        ticketId = TicketId("ticket-alice"),
        playerId = PlayerId("alice"),
        roomId = RoomId("room-alice"),
        createdAt = EpochMillis(1_000L),
        startsAt = EpochMillis(6_000L),
        deadline = EpochMillis(9_000L),
        serverTime = EpochMillis(1_000L),
        participants = Vector(
          BattleQueueParticipant(
            playerId = PlayerId("alice"),
            handle = PlayerHandle("alice"),
            joinedAt = EpochMillis(1_000L),
            lastSeen = EpochMillis(1_000L),
            rating = Some(Rating(1200)),
            avatar = Some("blue"),
            skin = Some("pilot")
          )
        ),
        capacity = BattleCapacity(2),
        durationMs = DurationMillis(300_000L),
        phase = MatchmakingRoomPhase.Waiting,
        finishedAt = None,
        battleSession = None
      )
    }

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      failUnused()

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      failUnused()

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      failUnused()

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      failUnused()
  }

  private object RecordingBattleQueueService {
    def apply(): RecordingBattleQueueService =
      new RecordingBattleQueueService()
  }

  private final class RecordingJoinAuthorizationService(
    result: Either[BattleQueueJoinAuthorizationError, Unit]
  ) extends BattleQueueJoinAuthorizationService {
    private var recordedCommands: Vector[BattleQueueJoinCommand] = Vector.empty

    def commands: Vector[BattleQueueJoinCommand] =
      recordedCommands

    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] = {
      recordedCommands = recordedCommands :+ command
      result
    }
  }

  private object RecordingJoinAuthorizationService {
    def apply(result: Either[BattleQueueJoinAuthorizationError, Unit]): RecordingJoinAuthorizationService =
      new RecordingJoinAuthorizationService(result)
  }

  private object UnusedBattleStateService extends BattleStateService {
    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      failUnused()

    override def acceptCommand(request: slaydemo.backend.battle.api.BattleCommandRequest) =
      failUnused()
  }

  private def failUnused[A](): A =
    throw new AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, text: String, expected: String): Unit =
    assert(text.contains(expected), s"$label: expected response to contain $expected, got $text")
}
