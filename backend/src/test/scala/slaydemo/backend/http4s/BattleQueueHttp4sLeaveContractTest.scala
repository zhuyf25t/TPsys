package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request, Uri}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.http4s.Http4sRouteContractSupport.{RouteResponse, runRoute}
import slaydemo.backend.identity.objects.PlayerHandle

object BattleQueueHttp4sLeaveContractTest {
  def main(args: Array[String]): Unit = {
    leaveReturnsTrueWhenQueueReportsLeft()
    leaveReturnsFalseWhenQueueReportsNotWaiting()
    missingTicketIdIsBadRequest()
    nonObjectBodyIsBadRequest()
    unsupportedMethodIsRejected()

    println("Battle queue http4s leave contract checks passed")
  }

  private def leaveReturnsTrueWhenQueueReportsLeft(): Unit = {
    val service = RecordingBattleQueueService(BattleQueueLeaveOutcome.LeftQueue)
    val response = postJson(service, uri"/api/battle/queue/leave", """{"ticketId":"ticket-route"}""")

    assertEquals("leave status", response.status, 200)
    assertEquals("leave body", response.body, """{"left":true}""")
    assertEquals("leave calls", service.leaveCalls, Vector(TicketId("ticket-route")))
  }

  private def leaveReturnsFalseWhenQueueReportsNotWaiting(): Unit = {
    val service = RecordingBattleQueueService(BattleQueueLeaveOutcome.NotWaiting)
    val response = postJson(service, uri"/battle/queue/leave", """{"ticketId":"ticket-route"}""")

    assertEquals("not waiting status", response.status, 200)
    assertEquals("not waiting body", response.body, """{"left":false}""")
    assertEquals("not waiting calls", service.leaveCalls, Vector(TicketId("ticket-route")))
  }

  private def missingTicketIdIsBadRequest(): Unit = {
    val service = RecordingBattleQueueService(BattleQueueLeaveOutcome.LeftQueue)
    val response = postJson(service, uri"/api/battle/queue/leave", "{}")

    assertEquals("missing ticket status", response.status, 400)
    assertEquals("missing ticket body", response.body, """{"error":"ticketId is required.","code":"bad_request"}""")
    assertEquals("missing ticket no service call", service.leaveCalls, Vector.empty)
  }

  private def nonObjectBodyIsBadRequest(): Unit = {
    val service = RecordingBattleQueueService(BattleQueueLeaveOutcome.LeftQueue)
    val response = postJson(service, uri"/api/battle/queue/leave", "[]")

    assertEquals("non-object status", response.status, 400)
    assertEquals(
      "non-object body",
      response.body,
      """{"error":"Request body must be a JSON object with supported primitive or object fields.","code":"bad_request"}"""
    )
    assertEquals("non-object no service call", service.leaveCalls, Vector.empty)
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingBattleQueueService(BattleQueueLeaveOutcome.LeftQueue)
    val request = Request[IO](method = Method.GET, uri = uri"/api/battle/queue/leave")
    val response = run(service, request)

    assertEquals("unsupported method status", response.status, 405)
    assertEquals(
      "unsupported method body",
      response.body,
      """{"error":"Only POST and OPTIONS are supported.","code":"method_not_allowed"}"""
    )
    assertEquals("unsupported method no service call", service.leaveCalls, Vector.empty)
  }

  private def postJson(service: RecordingBattleQueueService, targetUri: Uri, body: String): RouteResponse = {
    val request = Request[IO](method = Method.POST, uri = targetUri)
      .withEntity(body)
      .putHeaders(`Content-Type`(MediaType.application.json))
    run(service, request)
  }

  private def run(service: RecordingBattleQueueService, request: Request[IO]): RouteResponse = {
    runRoute(BattleQueueHttp4sRoutes.leaveRoutes(service), request)
  }

  private final class RecordingBattleQueueService(outcome: BattleQueueLeaveOutcome) extends BattleQueueService {
    var leaveCalls: Vector[TicketId] = Vector.empty

    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      failUnused()

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      failUnused()

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome = {
      leaveCalls = leaveCalls :+ ticketId
      outcome
    }

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
    def apply(outcome: BattleQueueLeaveOutcome): RecordingBattleQueueService =
      new RecordingBattleQueueService(outcome)
  }

  private def failUnused[A](): A =
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
