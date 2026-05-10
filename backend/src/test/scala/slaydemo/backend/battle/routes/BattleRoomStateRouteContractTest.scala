package slaydemo.backend.battle.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.identity.objects.PlayerHandle

object BattleRoomStateRouteContractTest {
  def main(args: Array[String]): Unit = {
    queueStatusReturnsSnapshot()
    queueLeaveReturnsOutcome()
    roomSnapshotAndHeartbeatUsePathRoomId()
    stateReadMapsSuccessAndNotFound()
    stateStreamEmitsFinishedStateAndCloses()

    println("Battle room/state route contract checks passed")
  }

  private def queueStatusReturnsSnapshot(): Unit = {
    val queueService = RecordingBattleQueueService()

    withBattleServer(queueService, RecordingBattleStateService()) { uri =>
      val response = get(uri.resolve("/battle/queue/status?ticketId=ticket-route"))

      assertEquals("status code", response.status, 200)
      assertContains("status ticket", response.body, """"ticketId":"ticket-route"""")
      assertContains("status phase", response.body, """"phase":"waiting"""")
      assertEquals("status calls", queueService.statusCalls, Vector(TicketId("ticket-route")))
    }
  }

  private def queueLeaveReturnsOutcome(): Unit = {
    val queueService = RecordingBattleQueueService()

    withBattleServer(queueService, RecordingBattleStateService()) { uri =>
      val response = postJson(uri.resolve("/battle/queue/leave"), """{"ticketId":"ticket-route"}""")

      assertEquals("leave status", response.status, 200)
      assertEquals("leave body", response.body, """{"left":true}""")
      assertEquals("leave calls", queueService.leaveCalls, Vector(TicketId("ticket-route")))
    }
  }

  private def roomSnapshotAndHeartbeatUsePathRoomId(): Unit = {
    val queueService = RecordingBattleQueueService()

    withBattleServer(queueService, RecordingBattleStateService()) { uri =>
      val snapshot = get(uri.resolve("/battle/rooms/room-route/snapshot"))
      val heartbeat = postJson(
        uri.resolve("/battle/rooms/room-route/heartbeat"),
        """{"ticketId":"ticket-route","handle":"Alice"}"""
      )

      assertEquals("room snapshot status", snapshot.status, 200)
      assertContains("room snapshot id", snapshot.body, """"roomId":"room-route"""")
      assertEquals("room snapshot calls", queueService.roomSnapshotCalls, Vector(RoomId("room-route")))
      assertEquals("heartbeat status", heartbeat.status, 200)
      assertContains("heartbeat room id", heartbeat.body, """"roomId":"room-route"""")
      assertEquals("heartbeat call count", queueService.heartbeatCalls.length, 1)
      val command = queueService.heartbeatCalls.head
      assertEquals("heartbeat path room id", command.roomId, Some(RoomId("room-route")))
      assertEquals("heartbeat ticket id", command.ticketId, Some(TicketId("ticket-route")))
      assertEquals("heartbeat handle", command.handle, Some(PlayerHandle("Alice")))
    }
  }

  private def stateReadMapsSuccessAndNotFound(): Unit = {
    val stateService = RecordingBattleStateService()

    withBattleServer(RecordingBattleQueueService(), stateService) { uri =>
      val success = get(uri.resolve("/battle/state/battle-route"))
      val missing = get(uri.resolve("/battle/state/missing"))

      assertEquals("state success status", success.status, 200)
      assertContains("state success battle id", success.body, """"battleId":"battle-route"""")
      assertContains("state success phase", success.body, """"phase":"active"""")
      assertEquals("state missing status", missing.status, 404)
      assertContains("state missing code", missing.body, """"code":"battle_not_found"""")
      assertEquals("state read calls", stateService.readCalls, Vector(BattleId("battle-route"), BattleId("missing")))
    }
  }

  private def stateStreamEmitsFinishedStateAndCloses(): Unit = {
    val stateService = RecordingBattleStateService()
    stateService.statesById = Map(BattleId("battle-route") -> battleState(phase = BattlePhase.Finished))

    withBattleServer(RecordingBattleQueueService(), stateService) { uri =>
      val response = get(uri.resolve("/battle/state/stream?battleId=battle-route"))

      assertEquals("state stream status", response.status, 200)
      assertContains("state stream event", response.body, "event: state")
      assertContains("state stream battle id", response.body, """"battleId":"battle-route"""")
      assertContains("state stream finished phase", response.body, """"phase":"finished"""")
      assertEquals("state stream read calls", stateService.readCalls, Vector(BattleId("battle-route")))
    }
  }

  private def withBattleServer[A](
    queueService: RecordingBattleQueueService,
    stateService: RecordingBattleStateService
  )(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = BattleRoutes(queueService, stateService, UnusedJoinAuthorizationService)
    server.createContext("/battle/queue/status", exchange => routes.status(exchange))
    server.createContext("/battle/queue/leave", exchange => routes.leave(exchange))
    server.createContext("/battle/rooms", exchange => routes.rooms(exchange))
    server.createContext("/battle/state", exchange => routes.state(exchange))
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

  private final class RecordingBattleQueueService extends BattleQueueService {
    var statusCalls: Vector[TicketId] = Vector.empty
    var leaveCalls: Vector[TicketId] = Vector.empty
    var roomSnapshotCalls: Vector[RoomId] = Vector.empty
    var heartbeatCalls: Vector[RealtimeRoomHeartbeatCommand] = Vector.empty

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      failUnused()

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] = {
      statusCalls = statusCalls :+ ticketId
      Right(queueSnapshot(ticketId))
    }

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome = {
      leaveCalls = leaveCalls :+ ticketId
      BattleQueueLeaveOutcome.LeftQueue
    }

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] = {
      roomSnapshotCalls = roomSnapshotCalls :+ roomId
      Right(roomSnapshotFor(roomId))
    }

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] = {
      heartbeatCalls = heartbeatCalls :+ request
      Right(roomSnapshotFor(request.roomId.getOrElse(RoomId("room-route"))))
    }

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      failUnused()

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      failUnused()
  }

  private final class RecordingBattleStateService extends BattleStateService {
    var statesById: Map[BattleId, BattleAggregateState] = Map(BattleId("battle-route") -> battleState())
    var readCalls: Vector[BattleId] = Vector.empty

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] = {
      readCalls = readCalls :+ battleId
      statesById.get(battleId).toRight(BattleStateReadError.BattleNotFound)
    }

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] =
      failUnused()
  }

  private object UnusedJoinAuthorizationService extends BattleQueueJoinAuthorizationService {
    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] =
      failUnused()
  }

  private def queueSnapshot(ticketId: TicketId): BattleQueueSnapshot =
    BattleQueueSnapshot(
      ticketId = ticketId,
      playerId = PlayerId("player-route"),
      roomId = RoomId("room-route"),
      createdAt = EpochMillis(1_000L),
      startsAt = EpochMillis(6_000L),
      deadline = EpochMillis(6_000L),
      serverTime = EpochMillis(1_500L),
      participants = Vector(participant()),
      capacity = BattleCapacity(2),
      durationMs = DurationMillis(5_000L),
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  private def roomSnapshotFor(roomId: RoomId): RealtimeRoomSnapshot =
    RealtimeRoomSnapshot(
      roomId = roomId,
      serverTime = EpochMillis(1_500L),
      participants = Vector(participant()),
      capacity = BattleCapacity(2),
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  private def participant(): BattleQueueParticipant =
    BattleQueueParticipant(
      playerId = PlayerId("player-route"),
      handle = PlayerHandle("Alice"),
      joinedAt = EpochMillis(1_000L),
      lastSeen = EpochMillis(1_500L),
      rating = None,
      avatar = None,
      skin = None
    )

  private def battleState(): BattleAggregateState =
    battleState(phase = BattlePhase.Active)

  private def battleState(phase: BattlePhase): BattleAggregateState =
    BattleAggregateState(
      battleId = BattleId("battle-route"),
      roomId = RoomId("room-route"),
      phase = phase,
      serverTime = EpochMillis(1_500L),
      startedAt = EpochMillis(1_000L),
      durationMs = DurationMillis(60_000L),
      elapsedMs = ElapsedMillis(500L),
      endsAt = EpochMillis(61_000L),
      worldSize = BattleVector2(1280.0, 720.0),
      tick = BattleTick(15L),
      artifactStatus = BattleArtifactStatus.Pending,
      players = Vector.empty,
      projectiles = Vector.empty,
      projectileTerminals = Vector.empty,
      slowFields = Vector.empty,
      pickups = Vector.empty,
      replayFrames = Vector.empty,
      events = Vector.empty,
      winnerPlayerId = None,
      winnerHeroId = None
    )

  private def failUnused[A](): A =
    throw new AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
