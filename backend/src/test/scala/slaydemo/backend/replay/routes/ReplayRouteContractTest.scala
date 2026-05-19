package slaydemo.backend.replay.routes

import java.net.{InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, RatingDelta, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.objects.{
  ReplayCommentId,
  ReplayCommentRecord,
  ReplayFrameCount,
  ReplayFramesJson,
  ReplayId,
  ReplayPlaybackAvailability,
  ReplayRecord,
  ReplaySettlementRecord,
  ReplayTitle
}
import slaydemo.backend.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}
import slaydemo.backend.shared.api.BackendAPIExchangeRouter

object ReplayRouteContractTest {
  private val ValidRecordJson: String =
    """{"replayId":"route-replay","battleId":"battle-route","handle":"Alice","displayName":"Alice","finishedAt":1000,"finishedAtLabel":"Finished","title":"Route Replay","modeLabel":"Arena","resultLabel":"Victory","mapLabel":"Map","highlightLine":"Great","coverLabel":"Cover","playersLine":"Alice | Bob","timelineHint":"Done","score":12,"placement":1,"durationMs":1800,"aliveAtEnd":true,"thumbnailDataUrl":null,"currentLoadout":"Pistol","frameCount":2,"playbackAvailable":true,"frames":[{"elapsedMs":0},{"elapsedMs":16}]}"""

  def main(args: Array[String]): Unit = {
    validRecordPostReachesServiceWithRawFrames()
    apiMessageCatalogGetRendersList()
    detailSelectsSettlementByHandle()
    invalidReplayIdPathIsBadRequest()
    commentPostMapsSuccessAndErrors()

    println("Replay route contract checks passed")
  }

  private def validRecordPostReachesServiceWithRawFrames(): Unit = {
    val service = RecordingReplayService()

    withReplayServer(service) { uri =>
      val response = postJson(uri.resolve("/replay/catalog"), ValidRecordJson)

      assertEquals("record status", response.status, 201)
      assertContains("record response replay id", response.body, """"replayId":"route-replay"""")
      assertEquals("record command count", service.recordCommands.length, 1)
      val command = service.recordCommands.head
      assertEquals("record replay id", command.replayId, ReplayId("route-replay"))
      assertEquals("record battle id", command.battleId, BattleId("battle-route"))
      assertEquals("record handle", command.handle, PlayerHandle("Alice"))
      assertEquals("record display name", command.displayName, DisplayName("Alice"))
      assertEquals("record score", command.score, Score(12))
      assertEquals("record placement", command.placement, Some(BattlePlacement.unsafe(1)))
      assertEquals("record playback availability", command.requestedPlaybackAvailability, ReplayPlaybackAvailability.Available)
      assertEquals("record raw frames", command.framesJson, """[{"elapsedMs":0},{"elapsedMs":16}]""")
    }
  }

  private def apiMessageCatalogGetRendersList(): Unit = {
    val service = RecordingReplayService()
    service.records = Vector(replayRecord())

    withReplayApiMessageServer(service) { uri =>
      val response = get(uri.resolve("/api/replaycatalogapi?handle=Bob&limit=1"))

      assertEquals("api catalog status", response.status, 200)
      assertContains("api catalog replay id", response.body, """"replayId":"route-replay"""")
      assertContains("api catalog selected result", response.body, """"resultLabel":"Defeat"""")
      assertContains("api catalog selected score", response.body, """"score":3""")
    }
  }

  private def detailSelectsSettlementByHandle(): Unit = {
    val service = RecordingReplayService()
    val replay = replayRecord()
    service.records = Vector(replay)

    withReplayServer(service) { uri =>
      val response = get(uri.resolve("/replay/catalog/route-replay?handle=Bob"))

      assertEquals("detail status", response.status, 200)
      assertContains("detail selected handle", response.body, """"handle":"Bob"""")
      assertContains("detail selected display name", response.body, """"displayName":"Bob"""")
      assertContains("detail selected score", response.body, """"score":3""")
      assertContains("detail exposes frames", response.body, """"frames":[{"elapsedMs":0}]""")
      assertEquals("detail load count", service.loadedReplayIds, Vector(ReplayId("route-replay")))
    }
  }

  private def invalidReplayIdPathIsBadRequest(): Unit = {
    val service = RecordingReplayService()

    withReplayServer(service) { uri =>
      val response = get(uri.resolve("/replay/catalog/bad%20id"))

      assertEquals("invalid replay id status", response.status, 400)
      assertContains("invalid replay id code", response.body, """"code":"invalid_replay_id"""")
      assertEquals("invalid replay id does not call load", service.loadedReplayIds, Vector.empty)
    }
  }

  private def commentPostMapsSuccessAndErrors(): Unit = {
    val service = RecordingReplayService()
    service.records = Vector(replayRecord())
    service.commentResults = Vector(
      Right(
        ReplayCommentRecord(
          id = ReplayCommentId("comment-route"),
          replayId = ReplayId("route-replay"),
          authorHandle = PlayerHandle("Alice"),
          body = "first",
          createdAt = EpochMillis(2_000L)
        )
      ),
      Left(ReplayCommentError.InvalidBody),
      Left(ReplayCommentError.ReplayNotFound)
    )

    withReplayServer(service) { uri =>
      val success = postJson(uri.resolve("/replay/catalog/route-replay/comments"), """{"authorHandle":"Alice","body":" first "}""")
      val invalidBody = postJson(uri.resolve("/replay/catalog/route-replay/comments"), """{"authorHandle":"Alice","body":" "}""")
      val missingReplay = postJson(uri.resolve("/replay/catalog/route-replay/comments"), """{"authorHandle":"Alice","body":"again"}""")

      assertEquals("comment success status", success.status, 201)
      assertContains("comment success id", success.body, """"id":"comment-route"""")
      assertEquals("comment invalid body status", invalidBody.status, 400)
      assertContains("comment invalid body code", invalidBody.body, """"code":"invalid_body"""")
      assertEquals("comment missing replay status", missingReplay.status, 404)
      assertContains("comment missing replay code", missingReplay.body, """"code":"replay_not_found"""")
      assertEquals("comment command count", service.commentCommands.length, 3)
      assertEquals("comment command replay id", service.commentCommands.head.replayId, ReplayId("route-replay"))
      assertEquals("comment command author", service.commentCommands.head.authorHandle, PlayerHandle("Alice"))
      assertEquals("comment command body is route raw", service.commentCommands.head.body, " first ")
    }
  }

  private def withReplayServer[A](service: RecordingReplayService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val routes = ReplayRoutes(service)
    server.createContext("/replay/catalog", exchange => routes.handle(exchange))
    server.start()
    try run(URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/"))
    finally server.stop(0)
  }

  private def withReplayApiMessageServer[A](service: RecordingReplayService)(run: URI => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/api/replaycatalogapi",
      exchange => BackendAPIExchangeRouter.handle(ReplayCatalogAPIMessagePlanner.endpoint(service))(exchange)
    )
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

  private def replayRecord(): ReplayRecord =
    ReplayRecord(
      replayId = ReplayId("route-replay"),
      battleId = BattleId("battle-route"),
      handle = PlayerHandle("Alice"),
      displayName = DisplayName("Alice"),
      finishedAt = EpochMillis(1_000L),
      finishedAtLabel = "Finished",
      title = ReplayTitle.fromWire("Route Replay"),
      modeLabel = "Arena",
      resultLabel = "Victory",
      mapLabel = "Map",
      highlightLine = "Great",
      coverLabel = "Cover",
      playersLine = "Alice | Bob",
      timelineHint = "Done",
      score = Score(12),
      placement = Some(BattlePlacement.unsafe(1)),
      ratingBefore = Some(slaydemo.backend.battle.objects.Rating(1200)),
      ratingDelta = Some(RatingDelta(12)),
      ratingAfter = Some(slaydemo.backend.battle.objects.Rating(1212)),
      durationMs = DurationMillis(1_800L),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      thumbnailDataUrl = None,
      currentLoadout = Some("Pistol"),
      frameCount = ReplayFrameCount.fromWire(1),
      playbackAvailability = ReplayPlaybackAvailability.Unavailable,
      framesJson = ReplayFramesJson.fromNormalized("""[{"elapsedMs":0}]"""),
      settlements = Vector(
        ReplaySettlementRecord(
          handle = PlayerHandle("Alice"),
          displayName = DisplayName("Alice"),
          resultLabel = "Victory",
          highlightLine = "Great",
          score = Score(12),
          placement = Some(BattlePlacement.unsafe(1)),
          ratingBefore = Some(slaydemo.backend.battle.objects.Rating(1200)),
          ratingDelta = Some(RatingDelta(12)),
          ratingAfter = Some(slaydemo.backend.battle.objects.Rating(1212)),
          survivalOutcome = BattleSurvivalOutcome.Survived,
          currentLoadout = Some("Pistol")
        ),
        ReplaySettlementRecord(
          handle = PlayerHandle("Bob"),
          displayName = DisplayName("Bob"),
          resultLabel = "Defeat",
          highlightLine = "Downed",
          score = Score(3),
          placement = Some(BattlePlacement.unsafe(2)),
          ratingBefore = Some(slaydemo.backend.battle.objects.Rating(1000)),
          ratingDelta = Some(RatingDelta(-8)),
          ratingAfter = Some(slaydemo.backend.battle.objects.Rating(992)),
          survivalOutcome = BattleSurvivalOutcome.Eliminated,
          currentLoadout = None
        )
      )
    )

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingReplayService extends ReplayService {
    var records: Vector[ReplayRecord] = Vector.empty
    var commentResults: Vector[Either[ReplayCommentError, ReplayCommentRecord]] = Vector.empty
    private var recordedRecordCommands: Vector[ReplayRecordCommand] = Vector.empty
    private var recordedCommentCommands: Vector[ReplayCommentCommand] = Vector.empty
    private var recordedLoadedReplayIds: Vector[ReplayId] = Vector.empty

    def recordCommands: Vector[ReplayRecordCommand] =
      recordedRecordCommands

    def commentCommands: Vector[ReplayCommentCommand] =
      recordedCommentCommands

    def loadedReplayIds: Vector[ReplayId] =
      recordedLoadedReplayIds

    override def record(command: ReplayRecordCommand): Either[ReplayRecordError, ReplayRecord] = {
      recordedRecordCommands = recordedRecordCommands :+ command
      Right(replayRecord().copy(replayId = command.replayId, battleId = command.battleId, handle = command.handle))
    }

    override def list(limit: Int): Vector[ReplayRecord] =
      records.take(limit)

    override def load(replayId: ReplayId): Option[ReplayRecord] = {
      recordedLoadedReplayIds = recordedLoadedReplayIds :+ replayId
      records.find(_.replayId == replayId)
    }

    override def addComment(command: ReplayCommentCommand): Either[ReplayCommentError, ReplayCommentRecord] = {
      recordedCommentCommands = recordedCommentCommands :+ command
      commentResults match {
        case head +: tail =>
          commentResults = tail
          head
        case _ =>
          Left(ReplayCommentError.ReplayNotFound)
      }
    }

    override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
      Vector.empty
  }

  private object RecordingReplayService {
    def apply(): RecordingReplayService =
      new RecordingReplayService()
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
