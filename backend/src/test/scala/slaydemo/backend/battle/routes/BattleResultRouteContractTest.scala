package slaydemo.backend.battle.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultRecordError, BattleResultService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BattleResultRouteContractTest {
  private val ValidRecordJson: String =
    """{"battleId":" battle-route ","handle":"Alice","displayName":" ","finishedAt":1000,"finishedAtLabel":"Finished","durationMs":1800,"score":12,"placement":1,"aliveAtEnd":true,"ratingBefore":1200,"ratingDelta":12,"ratingAfter":1212,"resultLabel":"Victory","modeLabel":"Arena","mapLabel":"Map","highlightLine":"Great","playersLine":"Alice / Bob","timelineHint":"Done","currentLoadout":null}"""

  def main(args: Array[String]): Unit = {
    listParsesFiltersAndRendersRecords()
    invalidHandleFilterShortCircuitsList()
    recordPostParsesCommandAndValidationErrors()

    println("Battle result route contract checks passed")
  }

  private def listParsesFiltersAndRendersRecords(): Unit = {
    val service = RecordingBattleResultService()
    service.records = Vector(resultRecord(handle = PlayerHandle("Alice"), currentLoadout = Some("Pistol")))

    withResultServer(service) { uri =>
      val response = get(uri.resolve("/battle/results?handle=Alice&battleId=battle-route&limit=2"))

      assertEquals("list status", response.status, 200)
      assertContains("list wrapper", response.body, """"results":[""")
      assertContains("list result id", response.body, """"resultId":"battle-route:alice"""")
      assertContains("list loadout", response.body, """"currentLoadout":"Pistol"""")
      assertEquals("list service calls", service.listCalls, Vector((Some(PlayerHandle("Alice")), Some(BattleId("battle-route")), 2)))
    }
  }

  private def invalidHandleFilterShortCircuitsList(): Unit = {
    val service = RecordingBattleResultService()

    withResultServer(service) { uri =>
      val response = get(uri.resolve("/battle/results?handle=visitor"))

      assertEquals("invalid handle filter status", response.status, 200)
      assertEquals("invalid handle filter body", response.body, """{"results":[]}""")
      assertEquals("invalid handle filter avoids service", service.listCalls, Vector.empty)
    }
  }

  private def recordPostParsesCommandAndValidationErrors(): Unit = {
    val service = RecordingBattleResultService()

    withResultServer(service) { uri =>
      val success = postJson(uri.resolve("/battle/results"), ValidRecordJson)
      val visitor = postJson(uri.resolve("/battle/results"), ValidRecordJson.replace("\"handle\":\"Alice\"", "\"handle\":\"visitor\""))
      val invalidBattle = postJson(uri.resolve("/battle/results"), ValidRecordJson.replace("\" battle-route \"", "\"  \""))

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
    }

    val failingService = RecordingBattleResultService()
    failingService.recordResults = Vector(Left(BattleResultRecordError.VisitorNotAllowed))
    withResultServer(failingService) { uri =>
      val serviceFailure = postJson(uri.resolve("/battle/results"), ValidRecordJson)

      assertEquals("service record error status", serviceFailure.status, 403)
      assertContains("service record error code", serviceFailure.body, """"code":"visitor_not_allowed"""")
    }
  }

  private def withResultServer[A](service: RecordingBattleResultService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = BattleResultRoutes(service)
    server.createContext("/battle/results", exchange => routes.handle(exchange))
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
