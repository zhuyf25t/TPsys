package slaydemo.backend.battle.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*

object BattleCommandRouteContractTest {
  private val ValidCommandJson: String =
    """{"battleId":"battle-state-runtime","playerId":"alice","ticketId":"ticket-alice","clientTick":42,"clientCommandSeq":43,"movement":{"x":1.0,"y":0.0},"aim":{"x":1.0,"y":0.0},"primaryHeld":false,"reloadPressed":false,"switchWeaponDirection":2}"""

  def main(args: Array[String]): Unit = {
    validCommandReachesService()
    skillBooleansReachServiceAsTypedIntents()
    switchIndexReachesServiceAsTypedTarget()
    missingTicketIsUnauthorizedBeforeService()
    stringBooleanIsRejected()
    numericBattleIdIsRejected()
    stringClientTickIsRejected()
    stringVectorComponentIsRejected()

    println("Battle command route contract checks passed")
  }

  private def validCommandReachesService(): Unit = {
    val stateService = RecordingBattleStateService()

    withCommandServer(stateService) { uri =>
      val response = postJson(uri, ValidCommandJson)

      assertEquals("valid command status", response.status, 200)
      assertEquals("valid command reaches service count", stateService.requests.length, 1)
      val request = stateService.requests.head
      assertEquals("valid command battle id", request.battleId, BattleId("battle-state-runtime"))
      assertEquals("valid command player id", request.playerId, PlayerId("alice"))
      assertEquals("valid command ticket id", request.ticketId, TicketId("ticket-alice"))
      assertEquals("valid command switch direction is normalized", request.switchWeaponDirection, BattleWeaponSwitchDirection.Next)
      assertEquals("valid command has no skill intents", request.skillIntents, BattleCommandSkillIntents.empty)
    }
  }

  private def skillBooleansReachServiceAsTypedIntents(): Unit = {
    val stateService = RecordingBattleStateService()
    val body = ValidCommandJson.replace(
      "\"switchWeaponDirection\":2",
      "\"castDash\":true,\"castBlink\":true,\"castFreeze\":true,\"switchWeaponDirection\":2"
    )

    withCommandServer(stateService) { uri =>
      val response = postJson(uri, body)

      assertEquals("skill command status", response.status, 200)
      assertEquals(
        "skill command intents preserve application order",
        stateService.requests.head.skillIntents.values,
        Vector(SkillKind.Blink, SkillKind.Dash, SkillKind.Freeze)
      )
    }
  }

  private def switchIndexReachesServiceAsTypedTarget(): Unit = {
    val stateService = RecordingBattleStateService()
    val positiveBody = ValidCommandJson.replace(
      "\"switchWeaponDirection\":2",
      "\"switchWeaponDirection\":2,\"switchWeaponIndex\":3"
    )
    val negativeBody = ValidCommandJson.replace(
      "\"switchWeaponDirection\":2",
      "\"switchWeaponDirection\":2,\"switchWeaponIndex\":-1"
    )

    withCommandServer(stateService) { uri =>
      val positive = postJson(uri, positiveBody)
      val negative = postJson(uri, negativeBody)

      assertEquals("positive switch index status", positive.status, 200)
      assertEquals("negative switch index status", negative.status, 200)
      assertEquals("positive switch index is typed", stateService.requests(0).switchWeaponIndex, Some(BattleWeaponSwitchIndex(3)))
      assertEquals("negative switch index is dropped", stateService.requests(1).switchWeaponIndex, None)
    }
  }

  private def missingTicketIsUnauthorizedBeforeService(): Unit = {
    val stateService = RecordingBattleStateService()

    withCommandServer(stateService) { uri =>
      val response = postJson(uri, ValidCommandJson.replace("\"ticketId\":\"ticket-alice\",", ""))

      assertEquals("missing ticket status", response.status, 403)
      assertContains("missing ticket code", response.body, """"code":"command_not_authorized"""")
      assertEquals("missing ticket does not reach service", stateService.requests.length, 0)
    }
  }

  private def stringBooleanIsRejected(): Unit = {
    assertBadRequest(
      label = "string boolean",
      body = ValidCommandJson.replace("\"primaryHeld\":false", "\"primaryHeld\":\"false\""),
      expectedCode = "missing_primary_held"
    )
  }

  private def numericBattleIdIsRejected(): Unit = {
    assertBadRequest(
      label = "numeric battle id",
      body = ValidCommandJson.replace("\"battleId\":\"battle-state-runtime\"", "\"battleId\":123"),
      expectedCode = "missing_battle_id"
    )
  }

  private def stringClientTickIsRejected(): Unit = {
    assertBadRequest(
      label = "string client tick",
      body = ValidCommandJson.replace("\"clientTick\":42", "\"clientTick\":\"42\""),
      expectedCode = "missing_client_tick"
    )
  }

  private def stringVectorComponentIsRejected(): Unit = {
    assertBadRequest(
      label = "string vector component",
      body = ValidCommandJson.replace("\"movement\":{\"x\":1.0,\"y\":0.0}", "\"movement\":{\"x\":\"1.0\",\"y\":0.0}"),
      expectedCode = "missing_movement"
    )
  }

  private def assertBadRequest(label: String, body: String, expectedCode: String): Unit = {
    val stateService = RecordingBattleStateService()

    withCommandServer(stateService) { uri =>
      val response = postJson(uri, body)

      assertEquals(s"$label status", response.status, 400)
      assertContains(s"$label code", response.body, s""""code":"$expectedCode"""")
      assertEquals(s"$label does not reach service", stateService.requests.length, 0)
    }
  }

  private def withCommandServer[A](stateService: RecordingBattleStateService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = BattleRoutes(UnusedBattleQueueService, stateService, UnusedJoinAuthorizationService)
    server.createContext("/battle/commands", exchange => routes.commands(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/battle/commands"))
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

  private final class RecordingBattleStateService extends BattleStateService {
    private var recordedRequests: Vector[BattleCommandRequest] = Vector.empty

    def requests: Vector[BattleCommandRequest] =
      recordedRequests

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      Left(BattleStateReadError.BattleNotFound)

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] = {
      recordedRequests = recordedRequests :+ request
      Right(
        BattleCommandAccepted(
          battleId = request.battleId,
          acceptedTick = request.clientTick,
          acceptedCommandSeq = request.clientCommandSeq,
          serverTime = EpochMillis(1_234L),
          commandStatus = BattleCommandStatus.Applied,
          commandReason = None,
          outcomes = Vector.empty
        )
      )
    }
  }

  private object RecordingBattleStateService {
    def apply(): RecordingBattleStateService =
      new RecordingBattleStateService()
  }

  private object UnusedBattleQueueService extends BattleQueueService {
    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      failUnused()

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

  private object UnusedJoinAuthorizationService extends BattleQueueJoinAuthorizationService {
    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] =
      failUnused()
  }

  private def failUnused[A](): A =
    throw new AssertionError("unused dependency was called")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
