package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.{Method, Request}
import org.http4s.implicits.uri

import slaydemo.backend.battle.objects.{
  BattleId,
  BattlePlacement,
  BattleSurvivalOutcome,
  DurationMillis,
  EpochMillis,
  Rating,
  RatingDelta,
  Score
}
import slaydemo.backend.http4s.replay.ReplayHttp4sRoutes
import slaydemo.backend.http4s.Http4sRouteContractSupport.{RouteResponse, runRoute}
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

object ReplayHttp4sCatalogContractTest {
  def main(args: Array[String]): Unit = {
    catalogGetRendersSelectedSettlement()
    catalogAliasPathIsSupported()
    catalogPostRecordsReplay()
    detailAndCommentsUseFrontendProxyPaths()
    invalidReplayIdPathIsBadRequest()
    badJsonRecordPostUsesTypedDto()
    badJsonCommentPostUsesTypedDto()
    unsupportedMethodIsRejected()

    println("Replay http4s catalog contract checks passed")
  }

  private def catalogGetRendersSelectedSettlement(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/api/replaycatalogapi?handle=Bob&limit=1"))

    assertEquals("catalog status", response.status, 200)
    assertContains("catalog replay id", response.body, """"replayId":"route-replay"""")
    assertContains("catalog selected result", response.body, """"resultLabel":"Defeat"""")
    assertContains("catalog selected title", response.body, """"title":"Defeat - Finished"""")
    assertContains("catalog selected score", response.body, """"score":3""")
    assertContains("catalog selected placement", response.body, """"placement":2""")
  }

  private def catalogAliasPathIsSupported(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/replay/catalog?limit=1"))
    val apiAliasResponse = run(service, Request[IO](method = Method.GET, uri = uri"/api/replay/catalog?handle=Bob&limit=1"))

    assertEquals("catalog alias status", response.status, 200)
    assertContains("catalog alias replay id", response.body, """"replayId":"route-replay"""")
    assertContains("catalog alias base result", response.body, """"resultLabel":"Victory"""")
    assertEquals("api alias catalog status", apiAliasResponse.status, 200)
    assertContains("api alias catalog replay id", apiAliasResponse.body, """"replayId":"route-replay"""")
    assertContains("api alias catalog selected result", apiAliasResponse.body, """"resultLabel":"Defeat"""")
  }

  private def catalogPostRecordsReplay(): Unit = {
    val service = RecordingReplayService(Vector.empty)
    val response = run(service, Request[IO](method = Method.POST, uri = uri"/replay/catalog").withEntity(ValidRecordJson))

    assertEquals("record status", response.status, 201)
    assertContains("record replay id", response.body, """"replayId":"route-post-replay"""")
    assertEquals("record command count", service.recordCommands.length, 1)
    assertEquals("record raw frames", service.recordCommands.head.framesJson, """[{"elapsedMs":0},{"elapsedMs":16}]""")
  }

  private def detailAndCommentsUseFrontendProxyPaths(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    service.commentResults = Vector(
      Right(
        ReplayCommentRecord(
          id = ReplayCommentId("comment-http4s"),
          replayId = ReplayId("route-replay"),
          authorHandle = PlayerHandle("Alice"),
          body = "first",
          createdAt = EpochMillis(2_000L)
        )
      ),
      Left(ReplayCommentError.InvalidBody),
      Left(ReplayCommentError.ReplayNotFound)
    )

    val detail = run(service, Request[IO](method = Method.GET, uri = uri"/replay/catalog/route-replay?handle=Bob"))
    val initialComments = run(service, Request[IO](method = Method.GET, uri = uri"/replay/catalog/route-replay/comments"))
    val postedComment = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/replay/catalog/route-replay/comments")
        .withEntity("""{"authorHandle":"Alice","body":" first "}""")
    )
    val invalidBody = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/replay/catalog/route-replay/comments")
        .withEntity("""{"authorHandle":"Alice","body":" "}""")
    )
    val missingReplay = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/replay/catalog/route-replay/comments")
        .withEntity("""{"authorHandle":"Alice","body":"again"}""")
    )

    assertEquals("detail status", detail.status, 200)
    assertContains("detail selected handle", detail.body, """"handle":"Bob"""")
    assertContains("detail selected display name", detail.body, """"displayName":"Bob"""")
    assertContains("detail selected score", detail.body, """"score":3""")
    assertContains("detail frames", detail.body, """"frames":[{"elapsedMs":0}]""")
    assertEquals("initial comments status", initialComments.status, 200)
    assertContains("initial comments envelope", initialComments.body, """"comments":[]""")
    assertEquals("posted comment status", postedComment.status, 201)
    assertContains("posted comment id", postedComment.body, """"id":"comment-http4s"""")
    assertEquals("comment invalid body status", invalidBody.status, 400)
    assertContains("comment invalid body code", invalidBody.body, """"code":"invalid_body"""")
    assertEquals("comment missing replay status", missingReplay.status, 404)
    assertContains("comment missing replay code", missingReplay.body, """"code":"replay_not_found"""")
    assertEquals("comment command count", service.commentCommands.length, 3)
    assertEquals("comment command replay id", service.commentCommands.head.replayId, ReplayId("route-replay"))
    assertEquals("comment command author", service.commentCommands.head.authorHandle, PlayerHandle("Alice"))
    assertEquals("comment command body is raw", service.commentCommands.head.body, " first ")
    assert(service.loadedReplayIds.contains(ReplayId("route-replay")), "detail/comments should load route-replay")
  }

  private def invalidReplayIdPathIsBadRequest(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.GET, uri = org.http4s.Uri.unsafeFromString("/replay/catalog/bad%20id")))

    assertEquals("invalid replay id status", response.status, 400)
    assertContains("invalid replay id code", response.body, """"code":"invalid_replay_id"""")
    assertEquals("invalid replay id does not call load", service.loadedReplayIds, Vector.empty)
  }

  private def badJsonRecordPostUsesTypedDto(): Unit = {
    val service = RecordingReplayService(Vector.empty)
    val response = run(service, Request[IO](method = Method.POST, uri = uri"/replay/catalog").withEntity("{bad-json}"))

    assertEquals("typed error response status", response.status, 400)
    assertEquals(
      "typed error response body",
      response.body,
      """{"error":"Request body must be a JSON object.","code":"bad_request"}"""
    )
    assertEquals("bad json does not record replay", service.recordCommands, Vector.empty)
  }

  private def badJsonCommentPostUsesTypedDto(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.POST, uri = uri"/replay/catalog/route-replay/comments").withEntity("[]"))

    assertEquals("bad comment json response status", response.status, 400)
    assertEquals(
      "bad comment json response body",
      response.body,
      """{"error":"Request body must be a JSON object.","code":"bad_request"}"""
    )
    assertEquals("bad comment json does not add comment", service.commentCommands, Vector.empty)
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.PUT, uri = uri"/api/replaycatalogapi").withEntity("{}"))

    assertEquals("unsupported method status", response.status, 405)
    assertEquals("unsupported method body", response.body, """{"error":"Method is not allowed.","code":"method_not_allowed"}""")
  }

  private def run(service: RecordingReplayService, request: Request[IO]): RouteResponse = {
    runRoute(ReplayHttp4sRoutes.catalogRoutes(service), request)
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
      ratingBefore = Some(Rating(1200)),
      ratingDelta = Some(RatingDelta(12)),
      ratingAfter = Some(Rating(1212)),
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
          ratingBefore = Some(Rating(1200)),
          ratingDelta = Some(RatingDelta(12)),
          ratingAfter = Some(Rating(1212)),
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
          ratingBefore = Some(Rating(1000)),
          ratingDelta = Some(RatingDelta(-8)),
          ratingAfter = Some(Rating(992)),
          survivalOutcome = BattleSurvivalOutcome.Eliminated,
          currentLoadout = None
        )
      )
    )

  private val ValidRecordJson: String =
    """{"replayId":"route-post-replay","battleId":"battle-route","handle":"Alice","displayName":"Alice","finishedAt":1000,"finishedAtLabel":"Finished","title":"Route Replay","modeLabel":"Arena","resultLabel":"Victory","mapLabel":"Map","highlightLine":"Great","coverLabel":"Cover","playersLine":"Alice | Bob","timelineHint":"Done","score":12,"placement":1,"durationMs":1800,"aliveAtEnd":true,"thumbnailDataUrl":null,"currentLoadout":"Pistol","frameCount":2,"playbackAvailable":true,"frames":[{"elapsedMs":0},{"elapsedMs":16}]}"""

  private final class RecordingReplayService(records: Vector[ReplayRecord]) extends ReplayService {
    private var recordedRecordCommands: Vector[ReplayRecordCommand] =
      Vector.empty
    private var recordedCommentCommands: Vector[ReplayCommentCommand] =
      Vector.empty
    private var recordedLoadedReplayIds: Vector[ReplayId] =
      Vector.empty
    var commentResults: Vector[Either[ReplayCommentError, ReplayCommentRecord]] =
      Vector.empty

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
    def apply(records: Vector[ReplayRecord]): RecordingReplayService =
      new RecordingReplayService(records)
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
