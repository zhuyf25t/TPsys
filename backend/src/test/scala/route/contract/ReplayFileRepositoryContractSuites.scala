package route.contract

import java.nio.file.Files

import io.circe.parser.parse

import services.battle.objects.*
import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.database.FileReplayRepository
import services.replay.objects.*

private[contract] object ReplayFileRepositoryContractTest:
  def run(): Unit =
    fileRepositoryRoundTripsReplayCommentsAndSettlements()

  private def fileRepositoryRoundTripsReplayCommentsAndSettlements(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-replay-file-contract")
    val storagePath = directory.resolve("replays.json")
    val replay = replayRecord()
    val comment = replayComment()

    try {
      val writer = FileReplayRepository(storagePath)
      writer.saveReplay(replay)
      writer.saveComment(comment)

      val raw = Files.readString(storagePath)
      assert(parse(raw).isRight, s"replay file must be valid JSON, got $raw")
      ContractAssertions.assertContains("replay file schema", raw, """"schema" : "slay-demo.replay-catalog.v2"""")
      ContractAssertions.assertContains("replay file frames b64", raw, """"framesJsonB64"""")
      ContractAssertions.assertContains("replay file escaped comment", raw, "Nice \\\"shot\\\"")

      val reader = FileReplayRepository(storagePath)
      val loadedReplay = reader.findReplayById(replay.replayId).getOrElse {
        throw AssertionError("expected replay to reload from file")
      }
      val loadedComments = reader.listComments(replay.replayId, 10)

      ContractAssertions.assertEquals("replay round trip record", loadedReplay, replay)
      ContractAssertions.assertEquals("replay round trip comments", loadedComments, Vector(comment))
      ContractAssertions.assertEquals("replay next comment id advances", reader.nextCommentId(), ReplayCommentId("comment-000008"))
    } finally {
      Files.deleteIfExists(storagePath)
      Files.deleteIfExists(storagePath.resolveSibling("replays.json.tmp"))
      Files.deleteIfExists(directory)
    }
  }

  private def replayRecord(): ReplayRecord =
    ReplayRecord(
      replayId = ReplayId("replay-file-1"),
      battleId = BattleId("battle-file-1"),
      handle = PlayerHandle("Alice"),
      displayName = DisplayName("Alice \"Ace\""),
      finishedAt = EpochMillis(3000),
      finishedAtLabel = "just now",
      title = ReplayTitle.fromWire("Replay title"),
      modeLabel = "Arena Mode",
      resultLabel = "Victory",
      mapLabel = "Island",
      highlightLine = "Alice won",
      coverLabel = "Top 1",
      playersLine = "Alice vs Bob",
      timelineHint = "30s",
      score = Score(88),
      placement = Some(BattlePlacement.unsafe(1)),
      ratingBefore = Some(Rating(1200)),
      ratingDelta = Some(RatingDelta(12)),
      ratingAfter = Some(Rating(1212)),
      durationMs = DurationMillis(30000),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      thumbnailDataUrl = Some("data:image/png;base64,abc"),
      currentLoadout = Some("rifle \"quoted\""),
      frameCount = ReplayFrameCount.fromWire(1),
      playbackAvailability = ReplayPlaybackAvailability.Available,
      framesJson = ReplayFramesJson.fromNormalized("""[{"tick":1,"note":"A\nB"}]"""),
      settlements = Vector(
        ReplaySettlementRecord(
          handle = PlayerHandle("Alice"),
          displayName = DisplayName("Alice \"Ace\""),
          resultLabel = "Victory",
          highlightLine = "Alice won",
          score = Score(88),
          placement = Some(BattlePlacement.unsafe(1)),
          ratingBefore = Some(Rating(1200)),
          ratingDelta = Some(RatingDelta(12)),
          ratingAfter = Some(Rating(1212)),
          survivalOutcome = BattleSurvivalOutcome.Survived,
          currentLoadout = Some("rifle \"quoted\"")
        )
      )
    )

  private def replayComment(): ReplayCommentRecord =
    ReplayCommentRecord(
      id = ReplayCommentId("comment-000007"),
      replayId = ReplayId("replay-file-1"),
      authorHandle = PlayerHandle("Bob"),
      body = "Nice \"shot\"\nagain",
      createdAt = EpochMillis(4000)
    )
