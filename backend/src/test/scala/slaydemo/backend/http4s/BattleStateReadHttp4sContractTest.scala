package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Method, Request}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*

object BattleStateReadHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    stateReadMapsSuccessByQuery()
    stateReadMapsSuccessByPath()
    missingBattleIdIsBadRequest()
    battleNotFoundIsNotFound()
    headDoesNotCallService()

    println("Battle state read http4s contract checks passed")
  }

  private def stateReadMapsSuccessByQuery(): Unit = {
    val service = RecordingBattleStateService()
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battlestatereadapi?battleId=battle-route"))

    assertEquals("query state status", response.status, 200)
    assertContains("query battle id", response.body, """"battleId":"battle-route"""")
    assertContains("query phase", response.body, """"phase":"active"""")
    assertEquals("query read calls", service.readCalls, Vector(BattleId("battle-route")))
  }

  private def stateReadMapsSuccessByPath(): Unit = {
    val service = RecordingBattleStateService()
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/state/battle-route"))

    assertEquals("path state status", response.status, 200)
    assertContains("path battle id", response.body, """"battleId":"battle-route"""")
    assertEquals("path read calls", service.readCalls, Vector(BattleId("battle-route")))
  }

  private def missingBattleIdIsBadRequest(): Unit = {
    val service = RecordingBattleStateService()
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battlestatereadapi"))

    assertEquals("missing battle status", response.status, 400)
    assertEquals("missing battle body", response.body, """{"error":"battleId is required.","code":"invalid_battle_id"}""")
    assertEquals("missing battle no service call", service.readCalls, Vector.empty)
  }

  private def battleNotFoundIsNotFound(): Unit = {
    val service = RecordingBattleStateService(statesById = Map.empty)
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battlestatereadapi?battleId=missing"))

    assertEquals("not found status", response.status, 404)
    assertEquals("not found body", response.body, """{"error":"battle_not_found","code":"battle_not_found"}""")
    assertEquals("not found read calls", service.readCalls, Vector(BattleId("missing")))
  }

  private def headDoesNotCallService(): Unit = {
    val service = RecordingBattleStateService()
    val response = run(service, Request[IO](method = Method.HEAD, uri = uri"/api/battlestatereadapi?battleId=battle-route"))

    assertEquals("head status", response.status, 200)
    assertEquals("head body", response.body, "")
    assertEquals("head no service call", service.readCalls, Vector.empty)
  }

  private def run(service: RecordingBattleStateService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.battleStateReadRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingBattleStateService(
    var statesById: Map[BattleId, BattleAggregateState] = Map(BattleId("battle-route") -> battleState())
  ) extends BattleStateService {
    var readCalls: Vector[BattleId] = Vector.empty

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] = {
      readCalls = readCalls :+ battleId
      statesById.get(battleId).toRight(BattleStateReadError.BattleNotFound)
    }

    override def acceptCommand(request: slaydemo.backend.battle.api.BattleCommandRequest) =
      failUnused()
  }

  private object RecordingBattleStateService {
    def apply(statesById: Map[BattleId, BattleAggregateState] = Map(BattleId("battle-route") -> battleState())): RecordingBattleStateService =
      new RecordingBattleStateService(statesById)
  }

  private def battleState(): BattleAggregateState =
    BattleAggregateState(
      battleId = BattleId("battle-route"),
      roomId = RoomId("room-route"),
      phase = BattlePhase.Active,
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
