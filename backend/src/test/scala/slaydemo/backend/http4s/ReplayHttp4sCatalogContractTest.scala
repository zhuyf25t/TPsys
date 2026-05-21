package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
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
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.objects.{
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
    legacyCatalogPathIsSupported()
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

  private def legacyCatalogPathIsSupported(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.GET, uri = uri"/replay/catalog?limit=1"))

    assertEquals("legacy catalog status", response.status, 200)
    assertContains("legacy catalog replay id", response.body, """"replayId":"route-replay"""")
    assertContains("legacy catalog base result", response.body, """"resultLabel":"Victory"""")
  }

  private def unsupportedMethodIsRejected(): Unit = {
    val service = RecordingReplayService(Vector(replayRecord()))
    val response = run(service, Request[IO](method = Method.POST, uri = uri"/api/replaycatalogapi").withEntity("{}"))

    assertEquals("unsupported method status", response.status, 405)
    assertEquals("unsupported method body", response.body, """{"error":"Method is not allowed.","code":"method_not_allowed"}""")
  }

  private def run(service: RecordingReplayService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.replayCatalogRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
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

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingReplayService(records: Vector[ReplayRecord]) extends ReplayService {
    override def record(command: ReplayRecordCommand): Either[ReplayRecordError, ReplayRecord] =
      Left(ReplayRecordError.InvalidReplayId)

    override def list(limit: Int): Vector[ReplayRecord] =
      records.take(limit)

    override def load(replayId: ReplayId): Option[ReplayRecord] =
      records.find(_.replayId == replayId)

    override def addComment(command: ReplayCommentCommand): Either[ReplayCommentError, ReplayCommentRecord] =
      Left(ReplayCommentError.ReplayNotFound)

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
