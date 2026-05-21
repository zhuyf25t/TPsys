package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Method, Request}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*

object BattleStateStreamHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    stateStreamEmitsFinishedStateAndCloses()
    missingBattleIdIsBadRequest()
    battleNotFoundIsNotFound()

    println("Battle state stream http4s contract checks passed")
  }

  private def stateStreamEmitsFinishedStateAndCloses(): Unit = {
    val service = RecordingBattleStateService(
      statesById = Map(BattleId("battle-route") -> battleState(phase = BattlePhase.Finished))
    )
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/state/stream?battleId=battle-route"))

    assertEquals("state stream status", response.status, 200)
    assertContains("state stream content type", response.contentType, "text/event-stream")
    assertContains("state stream event", response.body, "event: state")
    assertContains("state stream battle id", response.body, """"battleId":"battle-route"""")
    assertContains("state stream finished phase", response.body, """"phase":"finished"""")
    assertEquals("state stream read calls", service.readCalls, Vector(BattleId("battle-route")))
  }

  private def missingBattleIdIsBadRequest(): Unit = {
    val service = RecordingBattleStateService()
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/state/stream"))

    assertEquals("missing stream battle status", response.status, 400)
    assertEquals("missing stream battle body", response.body, """{"error":"battleId is required.","code":"invalid_battle_id"}""")
    assertEquals("missing stream no service call", service.readCalls, Vector.empty)
  }

  private def battleNotFoundIsNotFound(): Unit = {
    val service = RecordingBattleStateService(statesById = Map.empty)
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/state/stream?battleId=missing"))

    assertEquals("stream not found status", response.status, 404)
    assertEquals("stream not found body", response.body, """{"error":"battle_not_found","code":"battle_not_found"}""")
    assertEquals("stream not found read calls", service.readCalls, Vector(BattleId("missing")))
  }

  private def run(service: RecordingBattleStateService, request: Request[IO]): RouteResponse = {
    val response = BattleStateHttp4sRoutes.streamRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(
      status = response.status.code,
      body = response.as[String].unsafeRunSync(),
      contentType = response.contentType.map(_.mediaType.toString).getOrElse("")
    )
  }

  private final case class RouteResponse(status: Int, body: String, contentType: String)

  private final class RecordingBattleStateService(
    var statesById: Map[BattleId, BattleAggregateState]
  ) extends BattleStateService {
    var readCalls: Vector[BattleId] = Vector.empty

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] = {
      readCalls = readCalls :+ battleId
      statesById.get(battleId).toRight(BattleStateReadError.BattleNotFound)
    }

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] =
      failUnused()
  }

  private object RecordingBattleStateService {
    def apply(statesById: Map[BattleId, BattleAggregateState] = Map(BattleId("battle-route") -> battleState())): RecordingBattleStateService =
      new RecordingBattleStateService(statesById)
  }

  private def battleState(phase: BattlePhase = BattlePhase.Active): BattleAggregateState =
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
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
