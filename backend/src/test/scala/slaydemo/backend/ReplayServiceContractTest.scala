package slaydemo.backend

import slaydemo.backend.battle.objects.{BattleId, DurationMillis, EpochMillis, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.database.InMemoryReplayRepository
import slaydemo.backend.replay.objects.ReplayId
import slaydemo.backend.replay.services.{
  DefaultReplayService,
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError
}

object ReplayServiceContractTest {
  def main(args: Array[String]): Unit = {
    recordNormalizesReplayFieldsAndSupportsListLoad()
    commentsAreOrderedLimitedAndExplicitAboutMissingReplay()

    println("Replay service contract checks passed")
  }

  private def recordNormalizesReplayFieldsAndSupportsListLoad(): Unit = {
    val repository = InMemoryReplayRepository()
    val service = DefaultReplayService(repository, () => 1_000L)
    assertEquals(
      "unsafe replay id is rejected",
      service.record(
        replayCommand(
          replayId = ReplayId("unsafe/id"),
          finishedAt = EpochMillis(400L),
          frameCount = 0,
          framesJson = "[]"
        )
      ),
      Left(ReplayRecordError.InvalidReplayId)
    )
    assertEquals(
      "invalid frames json is rejected",
      service.record(
        replayCommand(
          replayId = ReplayId("replay-invalid"),
          finishedAt = EpochMillis(500L),
          frameCount = 1,
          playbackAvailable = true,
          framesJson = "{bad-json}"
        )
      ),
      Left(ReplayRecordError.InvalidFramesJson)
    )
    val first = recordReplay(service,
      replayCommand(
        replayId = ReplayId("replay-one"),
        finishedAt = EpochMillis(1_000L),
        frameCount = 1,
        playbackAvailable = true,
        framesJson = "",
        currentLoadout = Some("   "),
        thumbnailDataUrl = Some("  ")
      )
    )
    val second = recordReplay(service,
      replayCommand(
        replayId = ReplayId("replay-two"),
        finishedAt = EpochMillis(2_000L),
        frameCount = 2,
        playbackAvailable = true,
        framesJson = """[{"elapsedMs":0}]""",
        currentLoadout = Some("Pistol"),
        thumbnailDataUrl = Some("data:image/png;base64,abc")
      )
    )
    val third = recordReplay(service,
      replayCommand(
        replayId = ReplayId("replay-three"),
        finishedAt = EpochMillis(3_000L),
        frameCount = 0,
        playbackAvailable = false,
        framesJson = """[{"elapsedMs":0},{"elapsedMs":16}]"""
      )
    )
    val fourth = recordReplay(service,
      replayCommand(
        replayId = ReplayId("replay-four"),
        finishedAt = EpochMillis(4_000L),
        frameCount = 0,
        playbackAvailable = true,
        framesJson = """[{"elapsedMs":0},{"elapsedMs":16}]"""
      )
    )

    assertEquals("invalid replay is not persisted", service.load(ReplayId("replay-invalid")), None)
    assertEquals("unsafe replay is not persisted", service.load(ReplayId("unsafe/id")), None)
    assertEquals("first frame count derived from blank frames", first.frameCount, 0)
    assertEquals("first playback requires at least two frames", first.playbackAvailable, false)
    assertEquals("first frames json normalized", first.framesJson, "[]")
    assertEquals("first blank loadout normalized", first.currentLoadout, None)
    assertEquals("first blank thumbnail normalized", first.thumbnailDataUrl, None)
    assertEquals("second frame count derived from frames", second.frameCount, 1)
    assertEquals("second playback unavailable with one frame", second.playbackAvailable, false)
    assertEquals("second frames json preserved", second.framesJson, """[{"elapsedMs":0}]""")
    assertEquals("second loadout preserved", second.currentLoadout, Some("Pistol"))
    assertEquals("second thumbnail preserved", second.thumbnailDataUrl, Some("data:image/png;base64,abc"))
    assertEquals("third frame count derived from frames", third.frameCount, 2)
    assertEquals("third playback honors submitted unavailable flag", third.playbackAvailable, false)
    assertEquals("fourth frame count derived from frames", fourth.frameCount, 2)
    assertEquals("fourth playback requires submitted flag and playable frames", fourth.playbackAvailable, true)
    assertEquals("load finds saved replay", service.load(first.replayId).map(_.replayId), Some(first.replayId))
    assertEquals("list newest first limit", service.list(1).map(_.replayId), Vector(fourth.replayId))
    assertEquals("list zero limit", service.list(0), Vector.empty)
  }

  private def commentsAreOrderedLimitedAndExplicitAboutMissingReplay(): Unit = {
    var now = 1_000L
    val service = DefaultReplayService(InMemoryReplayRepository(), () => now)
    val replay = recordReplay(service, replayCommand(ReplayId("commented-replay"), EpochMillis(1_000L)))

    assertEquals(
      "invalid replay id comment error",
      service.addComment(ReplayCommentCommand(ReplayId("bad/id"), PlayerHandle("Alice"), "Missing")),
      Left(ReplayCommentError.InvalidReplayId)
    )
    assertEquals(
      "missing replay comment error",
      service.addComment(ReplayCommentCommand(ReplayId("missing"), PlayerHandle("Alice"), "Missing")),
      Left(ReplayCommentError.ReplayNotFound)
    )
    assertEquals(
      "blank comment body error",
      service.addComment(ReplayCommentCommand(replay.replayId, PlayerHandle("Alice"), "   ")),
      Left(ReplayCommentError.InvalidBody)
    )
    assertEquals(
      "long comment body error",
      service.addComment(ReplayCommentCommand(replay.replayId, PlayerHandle("Alice"), "x" * 1001)),
      Left(ReplayCommentError.InvalidBody)
    )

    val first = service.addComment(ReplayCommentCommand(replay.replayId, PlayerHandle("Alice"), "  first  "))
      .fold(error => fail(s"first comment failed: $error"), value => value)
    now = 2_000L
    val second = service.addComment(ReplayCommentCommand(replay.replayId, PlayerHandle("Bob"), "second"))
      .fold(error => fail(s"second comment failed: $error"), value => value)
    now = 3_000L
    val third = service.addComment(ReplayCommentCommand(replay.replayId, PlayerHandle("Cara"), "third"))
      .fold(error => fail(s"third comment failed: $error"), value => value)

    assertEquals("comment ids", Vector(first.id.value, second.id.value, third.id.value), Vector("comment-000001", "comment-000002", "comment-000003"))
    assertEquals("comment timestamps", Vector(first.createdAt.value, second.createdAt.value, third.createdAt.value), Vector(1_000L, 2_000L, 3_000L))
    assertEquals("comment body is trimmed", first.body, "first")
    assertEquals("comment limit returns latest in chronological order", service.listComments(replay.replayId, 2).map(_.body), Vector("second", "third"))
    assertEquals("comment zero limit", service.listComments(replay.replayId, 0), Vector.empty)
    assertEquals("missing replay comments are hidden", service.listComments(ReplayId("missing"), 20), Vector.empty)
  }

  private def replayCommand(
    replayId: ReplayId,
    finishedAt: EpochMillis,
    frameCount: Int = 2,
    playbackAvailable: Boolean = false,
    framesJson: String = "[]",
    currentLoadout: Option[String] = Some("Pistol"),
    thumbnailDataUrl: Option[String] = None
  ): ReplayRecordCommand =
    ReplayRecordCommand(
      replayId = replayId,
      battleId = BattleId(s"battle-${replayId.value}"),
      handle = PlayerHandle("Alice"),
      displayName = DisplayName("Alice"),
      finishedAt = finishedAt,
      finishedAtLabel = "Finished",
      title = "Replay",
      modeLabel = "Authoritative",
      resultLabel = "Victory",
      mapLabel = "Arena",
      highlightLine = "Victory",
      coverLabel = "Cover",
      playersLine = "Alice / Bob",
      timelineHint = "Done",
      score = Score(12),
      placement = Some(1),
      durationMs = DurationMillis(1_800L),
      aliveAtEnd = true,
      thumbnailDataUrl = thumbnailDataUrl,
      currentLoadout = currentLoadout,
      frameCount = frameCount,
      playbackAvailable = playbackAvailable,
      framesJson = framesJson
    )

  private def recordReplay(
    service: DefaultReplayService,
    command: ReplayRecordCommand
  ) =
    service.record(command).fold(error => fail(s"record replay failed: $error"), value => value)

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
