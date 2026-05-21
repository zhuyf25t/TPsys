package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request, Uri}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*

object BattleCommandHttp4sContractTest {
  private val ValidCommandJson: String =
    """{"battleId":"battle-state-runtime","playerId":"alice","ticketId":"ticket-alice","clientTick":42,"clientCommandSeq":43,"movement":{"x":1.0,"y":0.0},"aim":{"x":1.0,"y":0.0},"primaryHeld":false,"reloadPressed":false,"switchWeaponDirection":2}"""

  def main(args: Array[String]): Unit = {
    validCommandReachesService()
    pluralCommandPathMatchesFrontendProxy()
    singularCommandPathAliasMatchesFrontendProxy()
    nullableOptionalFieldsReachServiceAsAbsent()
    skillBooleansReachServiceAsTypedIntents()
    switchIndexReachesServiceAsTypedTarget()
    missingTicketIsUnauthorizedBeforeService()
    stringBooleanIsRejected()
    numericBattleIdIsRejected()
    stringClientTickIsRejected()
    stringVectorComponentIsRejected()

    println("Battle command http4s contract checks passed")
  }

  private def singularCommandPathAliasMatchesFrontendProxy(): Unit = {
    val stateService = RecordingBattleStateService()
    val response = postJson(stateService, uri"/battlecommandapi", ValidCommandJson)

    assertEquals("singular command path alias status", response.status, 200)
    assertEquals("singular command path alias reaches service", stateService.requests.length, 1)
  }

  private def pluralCommandPathMatchesFrontendProxy(): Unit = {
    val stateService = RecordingBattleStateService()
    val response = postJson(stateService, uri"/battle/commands", ValidCommandJson)

    assertEquals("plural command path status", response.status, 200)
    assertEquals("plural command path reaches service", stateService.requests.length, 1)
  }

  private def validCommandReachesService(): Unit = {
    val stateService = RecordingBattleStateService()
    val response = postJson(stateService, uri"/api/battlecommandapi", ValidCommandJson)

    assertEquals("valid command status", response.status, 200)
    assertContains("valid command response battle id", response.body, """"battleId":"battle-state-runtime"""")
    assertEquals("valid command reaches service count", stateService.requests.length, 1)
    val request = stateService.requests.head
    assertEquals("valid command battle id", request.battleId, BattleId("battle-state-runtime"))
    assertEquals("valid command player id", request.playerId, PlayerId("alice"))
    assertEquals("valid command ticket id", request.ticketId, TicketId("ticket-alice"))
    assertEquals("valid command switch direction is normalized", request.switchWeaponDirection, BattleWeaponSwitchDirection.Next)
    assertEquals("valid command has no skill intents", request.skillIntents, BattleCommandSkillIntents.empty)
  }

  private def nullableOptionalFieldsReachServiceAsAbsent(): Unit = {
    val stateService = RecordingBattleStateService()
    val body = ValidCommandJson.replace(
      "\"switchWeaponDirection\":2",
      "\"pointerWorld\":null,\"switchWeaponIndex\":null,\"switchWeaponDirection\":2"
    )
    val response = postJson(stateService, uri"/api/battlecommandapi", body)

    assertEquals("nullable optional command status", response.status, 200)
    assertEquals("nullable optional command reaches service", stateService.requests.length, 1)
    assertEquals("nullable pointerWorld becomes absent", stateService.requests.head.pointerWorld, None)
    assertEquals("nullable switchWeaponIndex becomes absent", stateService.requests.head.switchWeaponIndex, None)
  }

  private def skillBooleansReachServiceAsTypedIntents(): Unit = {
    val stateService = RecordingBattleStateService()
    val body = ValidCommandJson.replace(
      "\"switchWeaponDirection\":2",
      "\"castDash\":true,\"castBlink\":true,\"castFreeze\":true,\"switchWeaponDirection\":2"
    )
    val response = postJson(stateService, uri"/api/battlecommandapi", body)

    assertEquals("skill command status", response.status, 200)
    assertEquals(
      "skill command intents preserve application order",
      stateService.requests.head.skillIntents.values,
      Vector(SkillKind.Blink, SkillKind.Dash, SkillKind.Freeze)
    )
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

    val positive = postJson(stateService, uri"/api/battlecommandapi", positiveBody)
    val negative = postJson(stateService, uri"/api/battlecommandapi", negativeBody)

    assertEquals("positive switch index status", positive.status, 200)
    assertEquals("negative switch index status", negative.status, 200)
    assertEquals("positive switch index is typed", stateService.requests(0).switchWeaponIndex, Some(BattleWeaponSwitchIndex(3)))
    assertEquals("negative switch index is dropped", stateService.requests(1).switchWeaponIndex, None)
  }

  private def missingTicketIsUnauthorizedBeforeService(): Unit = {
    val stateService = RecordingBattleStateService()
    val response = postJson(stateService, uri"/api/battlecommandapi", ValidCommandJson.replace("\"ticketId\":\"ticket-alice\",", ""))

    assertEquals("missing ticket status", response.status, 403)
    assertContains("missing ticket code", response.body, """"code":"command_not_authorized"""")
    assertEquals("missing ticket does not reach service", stateService.requests.length, 0)
  }

  private def stringBooleanIsRejected(): Unit =
    assertBadRequest(
      label = "string boolean",
      body = ValidCommandJson.replace("\"primaryHeld\":false", "\"primaryHeld\":\"false\""),
      expectedCode = "missing_primary_held"
    )

  private def numericBattleIdIsRejected(): Unit =
    assertBadRequest(
      label = "numeric battle id",
      body = ValidCommandJson.replace("\"battleId\":\"battle-state-runtime\"", "\"battleId\":123"),
      expectedCode = "missing_battle_id"
    )

  private def stringClientTickIsRejected(): Unit =
    assertBadRequest(
      label = "string client tick",
      body = ValidCommandJson.replace("\"clientTick\":42", "\"clientTick\":\"42\""),
      expectedCode = "missing_client_tick"
    )

  private def stringVectorComponentIsRejected(): Unit =
    assertBadRequest(
      label = "string vector component",
      body = ValidCommandJson.replace("\"movement\":{\"x\":1.0,\"y\":0.0}", "\"movement\":{\"x\":\"1.0\",\"y\":0.0}"),
      expectedCode = "missing_movement"
    )

  private def assertBadRequest(label: String, body: String, expectedCode: String): Unit = {
    val stateService = RecordingBattleStateService()
    val response = postJson(stateService, uri"/api/battlecommandapi", body)

    assertEquals(s"$label status", response.status, 400)
    assertContains(s"$label code", response.body, s""""code":"$expectedCode"""")
    assertEquals(s"$label does not reach service", stateService.requests.length, 0)
  }

  private def postJson(service: RecordingBattleStateService, targetUri: Uri, body: String): RouteResponse = {
    val request = Request[IO](method = Method.POST, uri = targetUri)
      .withEntity(body)
      .putHeaders(`Content-Type`(MediaType.application.json))
    val response = BackendHttp4sRoutes.battleCommandRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
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

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
