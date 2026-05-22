package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.{Method, Request}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.http4s.battle.BattleQueueHttp4sRoutes
import slaydemo.backend.http4s.Http4sRouteContractSupport.{RouteResponse, runRoute}
import slaydemo.backend.identity.objects.PlayerHandle

object BattleQueueHttp4sStatusContractTest {
  def main(args: Array[String]): Unit = {
    statusGetRendersQueueSnapshot()
    restStatusPathOmitsAbsentParticipantCosmetics()
    missingTicketIdIsBadRequest()
    ticketNotFoundIsNotFound()
    unsupportedMethodIsRejected()

    println("Battle queue http4s status contract checks passed")
  }

  private def statusGetRendersQueueSnapshot(): Unit = {
    val service = RecordingBattleQueueService(Right(snapshot()))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/queue/status?ticketId=ticket-alice"))

    assertEquals("status code", response.status, 200)
    assertContains("ticket id", response.body, """"ticketId":"ticket-alice"""")
    assertContains("player id", response.body, """"playerId":"alice"""")
    assertContains("room id", response.body, """"roomId":"room-alice"""")
    assertContains("participant handle", response.body, """"handle":"alice"""")
    assertContains("phase", response.body, """"phase":"waiting"""")
    assertContains("finishedAt null", response.body, """"finishedAt":null""")
    assertContains("battleSession null", response.body, """"battleSession":null""")
    assertEquals("status ticket", service.statusTicketIds, Vector(TicketId("ticket-alice")))
  }

  private def restStatusPathOmitsAbsentParticipantCosmetics(): Unit = {
    val service = RecordingBattleQueueService(Right(snapshot(rating = None, avatar = None, skin = None)))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/queue/status?ticketId=ticket-route"))

    assertEquals("rest status code", response.status, 200)
    assertContains("rest status ticket", response.body, """"ticketId":"ticket-alice"""")
    assertContains("rest status phase", response.body, """"phase":"waiting"""")
    assertContains("rest status battle session null", response.body, """"battleSession":null""")
    assertNotContains("rest status omits absent rating", response.body, """"rating":null""")
    assertNotContains("rest status omits absent avatar", response.body, """"avatar":null""")
    assertNotContains("rest status omits absent skin", response.body, """"skin":null""")
    assertEquals("rest status ticket id", service.statusTicketIds, Vector(TicketId("ticket-route")))
  }

  private def missingTicketIdIsBadRequest(): Unit = {
    val service = RecordingBattleQueueService(Right(snapshot()))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/queue/status"))

    assertEquals("missing ticket status", response.status, 400)
    assertEquals(
      "missing ticket body",
      response.body,
      """{"error":"ticketId query parameter is required.","code":"missing_ticket_id"}"""
    )
    assertEquals("missing ticket does not call service", service.statusTicketIds, Vector.empty)
  }

  private def ticketNotFoundIsNotFound(): Unit = {
    val service = RecordingBattleQueueService(Left(BattleQueueStatusError.TicketNotFound))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/battle/queue/status?ticketId=missing"))

    assertEquals("not found status", response.status, 404)
    assertEquals("not found body", response.body, """{"error":"Queue ticket was not found.","code":"ticket_not_found"}""")
    assertEquals("not found calls service", service.statusTicketIds, Vector(TicketId("missing")))
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingBattleQueueService(Right(snapshot()))
    val response = run(service, Request[IO](method = Method.POST, uri = uri"/api/battle/queue/status").withEntity("{}"))

    assertEquals("unsupported method status", response.status, 405)
    assertEquals(
      "unsupported method body",
      response.body,
      """{"error":"Only GET and OPTIONS are supported.","code":"method_not_allowed"}"""
    )
    assertEquals("unsupported method does not call service", service.statusTicketIds, Vector.empty)
  }

  private def run(service: RecordingBattleQueueService, request: Request[IO]): RouteResponse = {
    runRoute(BattleQueueHttp4sRoutes.statusRoutes(service), request)
  }

  private def snapshot(
    rating: Option[Rating] = Some(Rating(1200)),
    avatar: Option[String] = Some("blue"),
    skin: Option[String] = Some("pilot")
  ): BattleQueueSnapshot =
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
          rating = rating,
          avatar = avatar,
          skin = skin
        )
      ),
      capacity = BattleCapacity(2),
      durationMs = DurationMillis(300_000L),
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  private final class RecordingBattleQueueService(result: Either[BattleQueueStatusError, BattleQueueSnapshot])
      extends BattleQueueService {
    private var recordedStatusTicketIds: Vector[TicketId] = Vector.empty

    def statusTicketIds: Vector[TicketId] =
      recordedStatusTicketIds

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      failUnused()

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] = {
      recordedStatusTicketIds = recordedStatusTicketIds :+ ticketId
      result
    }

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      failUnused()

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      None

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      ()
  }

  private object RecordingBattleQueueService {
    def apply(result: Either[BattleQueueStatusError, BattleQueueSnapshot]): RecordingBattleQueueService =
      new RecordingBattleQueueService(result)
  }

  private def failUnused[A](): A =
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")

  private def assertNotContains(label: String, actual: String, unexpectedSubstring: String): Unit =
    assert(!actual.contains(unexpectedSubstring), s"$label: did not expect body to contain $unexpectedSubstring, got $actual")
}
