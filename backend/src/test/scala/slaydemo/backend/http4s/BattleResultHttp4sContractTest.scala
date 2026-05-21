package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request, Uri}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultRecordError, BattleResultService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BattleResultHttp4sContractTest {
  private val ValidRecordJson: String =
    """{"battleId":" battle-route ","handle":"Alice","displayName":" ","finishedAt":1000,"finishedAtLabel":"Finished","durationMs":1800,"score":12,"placement":1,"aliveAtEnd":true,"ratingBefore":1200,"ratingDelta":12,"ratingAfter":1212,"resultLabel":"Victory","modeLabel":"Arena","mapLabel":"Map","highlightLine":"Great","playersLine":"Alice / Bob","timelineHint":"Done","currentLoadout":null}"""

  def main(args: Array[String]): Unit = {
    listParsesFiltersAndRendersRecords()
    invalidHandleFilterShortCircuitsList()
    recordPostParsesCommandAndValidationErrors()
    battleResultsRestPathAndTypedErrorsRemainCovered()

    println("Battle result http4s contract checks passed")
  }

  private def listParsesFiltersAndRendersRecords(): Unit = {
    val service = RecordingBattleResultService()
    service.records = Vector(resultRecord(handle = PlayerHandle("Alice"), currentLoadout = Some("Pistol")))
    val response = get(service, uri"/api/battleresultsapi?handle=Alice&battleId=battle-route&limit=2")

    assertEquals("list status", response.status, 200)
    assertContains("list wrapper", response.body, """"results":[""")
    assertContains("list result id", response.body, """"resultId":"battle-route:alice"""")
    assertContains("list loadout", response.body, """"currentLoadout":"Pistol"""")
    assertEquals("list service calls", service.listCalls, Vector((Some(PlayerHandle("Alice")), Some(BattleId("battle-route")), 2)))
  }

  private def invalidHandleFilterShortCircuitsList(): Unit = {
    val service = RecordingBattleResultService()
    val response = get(service, uri"/api/battleresultsapi?handle=visitor")

    assertEquals("invalid handle filter status", response.status, 200)
    assertEquals("invalid handle filter body", response.body, """{"results":[]}""")
    assertEquals("invalid handle filter avoids service", service.listCalls, Vector.empty)
  }

  private def recordPostParsesCommandAndValidationErrors(): Unit = {
    val service = RecordingBattleResultService()
    val success = postJson(service, uri"/api/battleresultsapi", ValidRecordJson)
    val visitor = postJson(service, uri"/api/battleresultsapi", ValidRecordJson.replace("\"handle\":\"Alice\"", "\"handle\":\"visitor\""))
    val invalidBattle = postJson(service, uri"/api/battleresultsapi", ValidRecordJson.replace("\" battle-route \"", "\"  \""))

    assertEquals("record status", success.status, 201)
    assertContains("record response battle id", success.body, """"battleId":"battle-route"""")
    assertContains("record response display fallback", success.body, """"displayName":"Alice"""")
    assertContains("record response null loadout", success.body, """"currentLoadout":null""")
    assertEquals("record command count", service.recordCommands.length, 1)
    val command = service.recordCommands.head
    assertEquals("record battle id trim", command.battleId, BattleId("battle-route"))
    assertEquals("record handle", command.handle, PlayerHandle("Alice"))
    assertEquals("record display fallback", command.displayName, DisplayName("Alice"))
    assertEquals("record placement", command.placement, Some(BattlePlacement.unsafe(1)))
    assertEquals("record survival outcome", command.survivalOutcome, BattleSurvivalOutcome.Survived)
    assertEquals("record null loadout", command.currentLoadout, None)

    assertEquals("visitor status", visitor.status, 403)
    assertContains("visitor code", visitor.body, """"code":"visitor_not_allowed"""")
    assertEquals("invalid battle status", invalidBattle.status, 400)
    assertContains("invalid battle code", invalidBattle.body, """"code":"invalid_battle_id"""")
    assertEquals("invalid records do not call service", service.recordCommands.length, 1)

    val failingService = RecordingBattleResultService()
    failingService.recordResults = Vector(Left(BattleResultRecordError.VisitorNotAllowed))
    val serviceFailure = postJson(failingService, uri"/api/battleresultsapi", ValidRecordJson)

    assertEquals("service record error status", serviceFailure.status, 403)
    assertContains("service record error code", serviceFailure.body, """"code":"visitor_not_allowed"""")
  }

  private def battleResultsRestPathAndTypedErrorsRemainCovered(): Unit = {
    val listService = RecordingBattleResultService()
    listService.records = Vector(resultRecord(handle = PlayerHandle("Alice"), currentLoadout = Some("Pistol")))
    val listResponse = get(listService, uri"/api/battle/results?handle=Alice&battleId=battle-route&limit=2")

    assertEquals("battle results rest list status", listResponse.status, 200)
    assertContains("battle results rest list result id", listResponse.body, """"resultId":"battle-route:alice"""")
    assertEquals("battle results rest list service calls", listService.listCalls, Vector((Some(PlayerHandle("Alice")), Some(BattleId("battle-route")), 2)))

    val recordService = RecordingBattleResultService()
    val response = postJson(recordService, uri"/api/battle/results", "{bad-json}")

    assertEquals("typed error response status", response.status, 400)
    assertEquals(
      "typed error response body",
      response.body,
      """{"error":"Request body must be a JSON object.","code":"bad_request"}"""
    )
    assertEquals("typed error response avoids service", recordService.recordCommands, Vector.empty)
  }

  private def get(service: RecordingBattleResultService, targetUri: Uri): RouteResponse = {
    val request = Request[IO](method = Method.GET, uri = targetUri)
    val response = BackendHttp4sRoutes.battleResultRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private def postJson(service: RecordingBattleResultService, targetUri: Uri, body: String): RouteResponse = {
    val request = Request[IO](method = Method.POST, uri = targetUri)
      .withEntity(body)
      .putHeaders(`Content-Type`(MediaType.application.json))
    val response = BackendHttp4sRoutes.battleResultRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingBattleResultService extends BattleResultService {
    var records: Vector[BattleResultRecord] = Vector.empty
    var listCalls: Vector[(Option[PlayerHandle], Option[BattleId], Int)] = Vector.empty
    var recordCommands: Vector[BattleResultRecordCommand] = Vector.empty
    var recordResults: Vector[Either[BattleResultRecordError, BattleResultRecord]] = Vector.empty

    override def record(command: BattleResultRecordCommand): Either[BattleResultRecordError, BattleResultRecord] = {
      recordCommands = recordCommands :+ command
      takeRecordResult(
        Right(
          resultRecord(
            battleId = command.battleId,
            handle = command.handle,
            displayName = command.displayName,
            finishedAt = command.finishedAt,
            finishedAtLabel = command.finishedAtLabel,
            durationMs = command.durationMs,
            score = command.score,
            placement = command.placement,
            aliveAtEnd = BattleSurvivalOutcome.aliveAtEnd(command.survivalOutcome),
            ratingBefore = command.ratingBefore,
            ratingDelta = command.ratingDelta,
            ratingAfter = command.ratingAfter,
            resultLabel = command.resultLabel,
            modeLabel = command.modeLabel,
            mapLabel = command.mapLabel,
            highlightLine = command.highlightLine,
            playersLine = command.playersLine,
            timelineHint = command.timelineHint,
            currentLoadout = command.currentLoadout
          )
        )
      )
    }

    override def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord] = {
      listCalls = listCalls :+ (handle, battleId, limit)
      records.take(limit)
    }

    private def takeRecordResult(
      default: Either[BattleResultRecordError, BattleResultRecord]
    ): Either[BattleResultRecordError, BattleResultRecord] =
      recordResults match {
        case head +: tail =>
          recordResults = tail
          head
        case _ =>
          default
      }
  }

  private object RecordingBattleResultService {
    def apply(): RecordingBattleResultService =
      new RecordingBattleResultService()
  }

  private def resultRecord(
    battleId: BattleId = BattleId("battle-route"),
    handle: PlayerHandle = PlayerHandle("Alice"),
    displayName: DisplayName = DisplayName("Alice"),
    finishedAt: EpochMillis = EpochMillis(1_000L),
    finishedAtLabel: String = "Finished",
    durationMs: DurationMillis = DurationMillis(1_800L),
    score: Score = Score(12),
    placement: Option[BattlePlacement] = Some(BattlePlacement.unsafe(1)),
    aliveAtEnd: Boolean = true,
    ratingBefore: Rating = Rating(1200),
    ratingDelta: RatingDelta = RatingDelta(12),
    ratingAfter: Rating = Rating(1212),
    resultLabel: String = "Victory",
    modeLabel: String = "Arena",
    mapLabel: String = "Map",
    highlightLine: String = "Great",
    playersLine: String = "Alice / Bob",
    timelineHint: String = "Done",
    currentLoadout: Option[String] = None
  ): BattleResultRecord =
    BattleResultRecord(
      battleId = battleId,
      handle = handle,
      displayName = displayName,
      finishedAt = finishedAt,
      finishedAtLabel = finishedAtLabel,
      durationMs = durationMs,
      score = score,
      placement = placement,
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd),
      ratingBefore = ratingBefore,
      ratingDelta = ratingDelta,
      ratingAfter = ratingAfter,
      resultLabel = BattleResultLabel.fromWire(resultLabel),
      modeLabel = BattleModeLabel.fromWire(modeLabel),
      mapLabel = BattleMapLabel.fromWire(mapLabel),
      highlightLine = BattleHighlightLine.fromWire(highlightLine),
      playersLine = BattlePlayersLine.fromWire(playersLine),
      timelineHint = BattleTimelineHint.fromWire(timelineHint),
      currentLoadout = currentLoadout
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
