package slaydemo.backend.battle.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*

object BattleRoomStateRouteContractTest {
  def main(args: Array[String]): Unit = {
    stateReadMapsSuccessAndNotFound()
    stateStreamEmitsFinishedStateAndCloses()
    legacyApiAliasesRemainSupported()

    println("Battle room/state route contract checks passed")
  }

  private def stateReadMapsSuccessAndNotFound(): Unit = {
    val stateService = RecordingBattleStateService()

    withBattleServer(stateService) { uri =>
      val success = get(uri.resolve("/api/battle/state?battleId=battle-route"))
      val missing = get(uri.resolve("/api/battle/state?battleId=missing"))

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

    withBattleServer(stateService) { uri =>
      val response = get(uri.resolve("/api/battle/state/stream?battleId=battle-route"))

      assertEquals("state stream status", response.status, 200)
      assertContains("state stream event", response.body, "event: state")
      assertContains("state stream battle id", response.body, """"battleId":"battle-route"""")
      assertContains("state stream finished phase", response.body, """"phase":"finished"""")
      assertEquals("state stream read calls", stateService.readCalls, Vector(BattleId("battle-route")))
    }
  }

  private def legacyApiAliasesRemainSupported(): Unit = {
    val stateService = RecordingBattleStateService()
    stateService.statesById = Map(BattleId("battle-route") -> battleState(phase = BattlePhase.Finished))

    withBattleServer(stateService) { uri =>
      val stream = get(uri.resolve("/api/battlestatestreamapi?battleId=battle-route"))

      assertEquals("legacy state stream status", stream.status, 200)
      assertContains("legacy state stream event", stream.body, "event: state")
    }
  }

  private def withBattleServer[A](stateService: RecordingBattleStateService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = BattleRoutes(stateService)
    server.createContext("/api/battle/state/stream", exchange => routes.state(exchange))
    server.createContext("/api/battle/state", exchange => routes.state(exchange))
    server.createContext("/api/battlestatestreamapi", exchange => routes.state(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/"))
    finally server.stop(0)
  }

  private def get(uri: URI): RouteResponse = {
    val request = HttpRequest.newBuilder(uri).GET().build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    RouteResponse(response.statusCode(), response.body())
  }

  private final case class RouteResponse(status: Int, body: String)

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
